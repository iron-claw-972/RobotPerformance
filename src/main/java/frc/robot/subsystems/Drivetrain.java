package frc.robot.subsystems;

import java.util.function.DoubleSupplier;

import com.ctre.phoenix.sensors.WPI_Pigeon2;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.GenericEntry;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Robot;
import frc.robot.constants.Constants;
import frc.robot.constants.DriveConstants;
import frc.robot.constants.ModuleConstants;
import frc.robot.constants.DriveConstants.TestDriveConstants;
import frc.robot.util.LogManager;

/** Represents a swerve drive style drivetrain.
 * Module IDs are:
 * 1: Front left
 * 2: Front right
 * 3: Back left
 * 4: Back right
*/
public class Drivetrain extends SubsystemBase {

  // Swerve modules and other
  public SwerveModuleState[] m_swerveModuleStates = new SwerveModuleState[] {
    new SwerveModuleState(),
    new SwerveModuleState(),
    new SwerveModuleState(),
    new SwerveModuleState()
  };

  private final Module[] m_modules;

  // TODO: Should this be public?
  public final SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
    new Translation2d(DriveConstants.kTrackWidth / 2, DriveConstants.kTrackWidth / 2),
    new Translation2d(DriveConstants.kTrackWidth / 2, -DriveConstants.kTrackWidth / 2),
    new Translation2d(-DriveConstants.kTrackWidth / 2, DriveConstants.kTrackWidth / 2),
    new Translation2d(-DriveConstants.kTrackWidth / 2, -DriveConstants.kTrackWidth / 2)
  );

  // Pigeon
  private final WPI_Pigeon2 m_pigeon = new WPI_Pigeon2(DriveConstants.kPigeon, Constants.kCanivoreCAN);
  private boolean m_hasResetYaw = false; // the initial yaw has been set 

  public double m_headingPIDOutput = 0;

  // Odometry
  private final SwerveDriveOdometry m_odometry;
  private Pose2d m_robotPose = new Pose2d();

  // Displays the field with the robots estimated pose on it
  private final Field2d m_fieldDisplay = new Field2d();
  
  // PID Controllers
  private PIDController m_xController = new PIDController(0,0,0);
  private PIDController m_yController = new PIDController(0, 0, 0);
  private PIDController m_rotationController = new PIDController(DriveConstants.KheadingP, DriveConstants.KheadingI, DriveConstants.KheadingD);

  //Shuffleboard
  GenericEntry 
    m_driveVelocity,
    m_steerVelocity, 
    m_steerAngle, 
    m_drivetrainvolts, 
    m_driveStaticFeedforward, 
    m_driveVelocityFeedforward, 
    m_steerStaticFeedforward,
    m_steerVelocityFeedforward,
    m_heading;
  ShuffleboardTab m_swerveModulesTab,m_drivetrainTab;

  public Double[] m_driveVelFeedForwardSaver=new Double[4];
  public Double[] m_driveStaticFeedForwardSaver=new Double[4];
  public Double[] m_steerVelFeedForwardSaver=new Double[4];
  public Double[] m_steerStaticFeedForwardSaver=new Double[4];
  // modules needed to distigue in chooser
  Module m_prevModule;
  
  SendableChooser<Module> m_moduleChooser = new SendableChooser<>();




  public Drivetrain(ShuffleboardTab drivetrainTab, ShuffleboardTab swerveModulesTab) {
    LiveWindow.disableAllTelemetry();
    m_drivetrainTab = drivetrainTab;
    m_swerveModulesTab = swerveModulesTab;
    m_swerveModulesTab.add("Module Chooser", m_moduleChooser);
    
    m_modules = new Module[]{
        Module.create(ModuleConstants.COMP_FL, m_swerveModulesTab),
        Module.create(ModuleConstants.COMP_FR, m_swerveModulesTab),
        Module.create(ModuleConstants.COMP_BL, m_swerveModulesTab),
        Module.create(ModuleConstants.COMP_BR, m_swerveModulesTab)
      };
    m_prevModule = m_modules[0];

    m_odometry = new SwerveDriveOdometry(m_kinematics, m_pigeon.getRotation2d(), getModulePositions(), m_robotPose);
    m_rotationController.enableContinuousInput(-Math.PI,Math.PI);
    DoubleSupplier[] poseSupplier = {() -> getPose().getX(), () -> getPose().getY(), () -> getPose().getRotation().getRadians()};
    LogManager.addDoubleArray("Pose2d", poseSupplier);

    m_fieldDisplay.setRobotPose(getPose());
    SmartDashboard.putData("Field", m_fieldDisplay);
    
  }

  @Override
  public void periodic() {
    if (!Robot.isReal()) {
      for (int i = 0; i < m_modules.length; i++) {
        m_modules[i].periodic();
      }
    }
    updateOdometry();
    
    m_fieldDisplay.setRobotPose(getPose());
  }

  public void runChassisPID(double x, double y, double rot) {
    double xSpeed = m_xController.calculate(m_odometry.getPoseMeters().getX(), x);
    double ySpeed = m_yController.calculate(m_odometry.getPoseMeters().getY(), y);
    double rotRadians = m_rotationController.calculate(getAngleHeading(), rot);
    System.out.println(rotRadians);
    driveRot(xSpeed, ySpeed, rotRadians, true);
  }

  public void setPigeonYaw(double degrees) {
    m_pigeon.setYaw(degrees);
  }

  /**
   * Resets the pigeon yaw, but only if it hasn't already been reset. Will reset it to {@link DriveConstants.kStartingHeadingDegrees}
   */
  public void initializePigeonYaw(boolean force) {
    if (!m_hasResetYaw || force) {
      m_hasResetYaw = true;
      setPigeonYaw(DriveConstants.kStartingHeadingDegrees);
    }
  }

  /**
   * Method to drive the robot using joystick info.
   *
   * @param xSpeed speed of the robot in the x direction (forward)
   * @param ySpeed speed of the robot in the y direction (sideways)
   * @param rot angular rate of the robot
   * @param fieldRelative whether the provided x and y speeds are relative to the field
   */
  public void driveRot(double xSpeed, double ySpeed, double rot, boolean fieldRelative) {

    // TODO: Fix Swerve drive sim
    if (!Robot.isReal()) {
      m_pigeon.getSimCollection().addHeading(
        Units.radiansToDegrees(rot * Constants.kLoopTime));
    }

    m_swerveModuleStates =
        m_kinematics.toSwerveModuleStates(
            fieldRelative
                ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, m_pigeon.getRotation2d())
                : new ChassisSpeeds(xSpeed, ySpeed, rot));
    SwerveDriveKinematics.desaturateWheelSpeeds(m_swerveModuleStates, DriveConstants.kMaxSpeed);
    setModuleStates(m_swerveModuleStates);
  }

  public void driveHeading(double xSpeed, double ySpeed, double heading, boolean fieldRelative) {
    m_headingPIDOutput = m_rotationController.calculate(getAngleHeading(),heading);
    double rot = m_headingPIDOutput;

    // TODO: Fix Swerve drive sim
    // TODO: Check which lines were sapouse to be commented
    if (!Robot.isReal()) {
      // System.out.println(xSpeed + " " + ySpeed + " " + rot);
      // m_pigeon.getSimCollection().addHeading(rot / (2 * Math.PI));
      m_pigeon.getSimCollection().addHeading(Units.radiansToDegrees(rot * Constants.kLoopTime));
    }

    m_swerveModuleStates =
        m_kinematics.toSwerveModuleStates(
            fieldRelative
                ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, m_pigeon.getRotation2d())
                : new ChassisSpeeds(xSpeed, ySpeed, rot));
    SwerveDriveKinematics.desaturateWheelSpeeds(m_swerveModuleStates, DriveConstants.kMaxSpeed);
    setModuleStates(m_swerveModuleStates);
  }

  /** Updates the field relative position of the robot. */
  public void updateOdometry() {
    m_robotPose = m_odometry.update(
      m_pigeon.getRotation2d(),
      getModulePositions()
    );
  }

  /**
   * Returns the angular rate from the pigeon.
   * 
   * @param id 0 for x, 1 for y, 2 for z
   * @return the rate in rads/s from the pigeon
   */
  public double getAngularRate(int id) {
    double[] rawGyros = new double[3];
    m_pigeon.getRawGyro(rawGyros);
    return rawGyros[id] * Math.PI / 180;
  }

  /**
   * Gets the current robot pose from the odometry.
   */
  public Pose2d getPose() {
    return m_robotPose;
  }

  /**
   * Resets the odometry to the given pose and gyro angle.
   * @param pose current robot pose
   * @param gyroAngle current robot gyro angle
   */
  public void resetOdometry(Pose2d pose, Rotation2d gyroAngle){
    m_odometry.resetPosition(gyroAngle, getModulePositions(), pose);
  }

  /**
   * @return the pidgeon's Rotation2d
   */
  public Rotation2d getRotation2d(){
    return m_pigeon.getRotation2d(); 
  }

  /**
   * Gets the angle heading from the pigeon.
   * 
   * @return the heading angle in radians, from -pi to pi
   */
  public double getAngleHeading() {
    double angle = m_pigeon.getRotation2d().getRadians();
    return MathUtil.angleModulus(angle);
  }

  /**
   * Gets an array of all the swerve module positions.
   * 
   * @return an array of all swerve module positions
   */
  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[]{
      m_modules[0].getPosition(),
      m_modules[1].getPosition(),
      m_modules[2].getPosition(),
      m_modules[3].getPosition()
    };
    return positions;
  }

  /**
   * Sets the desired states for all swerve modules.
   * 
   * @param swerveModuleStates an array of module states to set swerve modules to. Order of the array matters here!
   */
  public void setModuleStates(SwerveModuleState[] swerveModuleStates) {
    for (int i = 0; i < 4; i++) {
      m_modules[i].setDesiredState(swerveModuleStates[i]);
    }
  }

  public void stop(){
    for (int i = 0; i < 4; i++) {
      m_modules[i].stop();
    }
  }

  public void driveVoltsTest(double volts) {
    // setAllOptimize(false);
    for (int i = 0; i < 4; i++) {
      m_modules[i].setDriveVoltage(volts);
    }
    m_modules[0].setSteerAngle(new Rotation2d(Units.degreesToRadians(135)));
    m_modules[1].setSteerAngle(new Rotation2d(Units.degreesToRadians(45)));
    m_modules[2].setSteerAngle(new Rotation2d(Units.degreesToRadians(225)));
    m_modules[3].setSteerAngle(new Rotation2d(Units.degreesToRadians(315)));
  }
  public void steerVoltsTest(double volts) {
    // setAllOptimize(false);
    for (int i = 0; i < 4; i++) {
      m_modules[i].setDriveVoltage(0);
      m_modules[i].setSteerVoltage(volts);
    }
  }
  public boolean isDriveVelocityAcurate(){
    return 
      Math.abs(m_modules[0].getDriveVelocityError()) < 0.1 &&
      Math.abs(m_modules[1].getDriveVelocityError()) < 0.1 &&
      Math.abs(m_modules[2].getDriveVelocityError()) < 0.1 &&
      Math.abs(m_modules[3].getDriveVelocityError()) < 0.1;
  }

  public boolean isSteerAngleAcurate(){
    return 
      Math.abs(m_modules[0].getSteerAngleError()) < Units.degreesToRadians(1) &&
      Math.abs(m_modules[1].getSteerAngleError()) < Units.degreesToRadians(1) &&
      Math.abs(m_modules[2].getSteerAngleError()) < Units.degreesToRadians(1) &&
      Math.abs(m_modules[3].getSteerAngleError()) < Units.degreesToRadians(1);
  }

  public double[] getDriveVelocities(){
    return new double[] {
      m_modules[0].getDriveVelocity(),
      m_modules[1].getDriveVelocity(),
      m_modules[2].getDriveVelocity(),
      m_modules[3].getDriveVelocity()
    };
  }
  public double[] getSteerVelocities(){
    return new double[] {
      m_modules[0].getSteerVelocity(),
      m_modules[1].getSteerVelocity(),
      m_modules[2].getSteerVelocity(),
      m_modules[3].getSteerVelocity()
    };
  }
  public void runCharacterizationVolts(int module, double value) {
    for (int i = 0; i < 4; i++) {
      m_modules[i].setDriveVoltage(0);
      if (module == i){
        m_modules[i].setSteerVoltage(value);
      } else {
        m_modules[i].setSteerVoltage(0);
      }
    }
  }
  

  public void setChassisSpeeds(ChassisSpeeds chassisSpeeds) {
    m_swerveModuleStates = m_kinematics.toSwerveModuleStates(chassisSpeeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(m_swerveModuleStates, DriveConstants.kMaxSpeed);
    setModuleStates(m_swerveModuleStates);
    
    if (!Robot.isReal()) {
      m_pigeon.getSimCollection().addHeading(
        Units.radiansToDegrees(chassisSpeeds.omegaRadiansPerSecond * Constants.kLoopTime));
    }
  }

  public PIDController getXController() {
      return m_xController;
  }
  public PIDController getYController() {
      return m_yController;
  }
  public PIDController getRotationController() {
    return m_rotationController;
  }
  public void setAllOptimize(Boolean optimizeSate){
    for (int i = 0; i < 4; i++) {
      m_modules[i].setOptimize(optimizeSate);
    }
  }
  public void setupDrivetrainShuffleboard() {
    // inputs
    m_heading = m_drivetrainTab.add("Set Heading (-pi to pi)", 0).getEntry();
    
    // add PID controlers
    m_drivetrainTab.add("xController", getXController());
    m_drivetrainTab.add("yController", getYController());
    m_drivetrainTab.add("rotationController", getRotationController());

    m_drivetrainTab.addNumber("getAngle", () -> getAngleHeading());
    m_drivetrainTab.addNumber("heading PID output", () -> m_headingPIDOutput);

    m_drivetrainTab.addNumber("Gyro X", () -> getAngularRate(0));
    m_drivetrainTab.addNumber("Gyro Y", () -> getAngularRate(1));
    m_drivetrainTab.addNumber("Gyro Z", () -> getAngularRate(2));

    m_drivetrainTab.add(getXController());
    m_drivetrainTab.add(getYController());
    m_drivetrainTab.add(getRotationController());
  }
  public void setupModulesShuffleboard(){
    // inputs
    m_driveVelocity = m_swerveModulesTab.add("Set Drive Velocity", 0).getEntry();
    m_steerVelocity = m_swerveModulesTab.add("Set Steer Velocity", 0).getEntry();
    m_steerAngle = m_swerveModulesTab.add("Set Steer Angle", 0).getEntry();
    m_drivetrainvolts = m_swerveModulesTab.add("Set Volts", 0).getEntry();
    m_driveStaticFeedforward = m_swerveModulesTab.add("Drive kS FF", 0).getEntry();
    m_driveVelocityFeedforward = m_swerveModulesTab.add("Drive kV FF", 0).getEntry();
    m_steerStaticFeedforward = m_swerveModulesTab.add("Steer kS FF", 0).getEntry();
    m_steerVelocityFeedforward = m_swerveModulesTab.add("Steer kV k FF", 0).getEntry();

    setUpFeedforwardHashmap();
    
    for (int i = 0; i < 4; i++){
      m_modules[i].setupModulesShuffleboard();
    }
  }
  private void setUpFeedforwardHashmap(){
    m_driveStaticFeedForwardSaver[0] = TestDriveConstants.kDriveKSFrontLeft;
    m_driveStaticFeedForwardSaver[1] = TestDriveConstants.kDriveKSFrontRight;
    m_driveStaticFeedForwardSaver[2] = TestDriveConstants.kDriveKSBackLeft;
    m_driveStaticFeedForwardSaver[3] = TestDriveConstants.kDriveKSBackRight;
    
    m_driveVelFeedForwardSaver[0] = TestDriveConstants.kDriveKVFrontLeft;
    m_driveVelFeedForwardSaver[1] = TestDriveConstants.kDriveKVFrontRight;
    m_driveVelFeedForwardSaver[2] = TestDriveConstants.kDriveKVBackLeft;
    m_driveVelFeedForwardSaver[3] = TestDriveConstants.kDriveKVBackRight;
    
    m_steerStaticFeedForwardSaver[0] = TestDriveConstants.kSteerKSFrontLeft;
    m_steerStaticFeedForwardSaver[1] = TestDriveConstants.kSteerKSFrontRight;
    m_steerStaticFeedForwardSaver[2] = TestDriveConstants.kSteerKSBackLeft;
    m_steerStaticFeedForwardSaver[3] = TestDriveConstants.kSteerKSBackRight;
    
    m_steerVelFeedForwardSaver[0] = TestDriveConstants.kSteerKVFrontLeft;
    m_steerVelFeedForwardSaver[1] = TestDriveConstants.kSteerKVFrontRight;
    m_steerVelFeedForwardSaver[2] = TestDriveConstants.kSteerKVBackLeft;
    m_steerVelFeedForwardSaver[3] = TestDriveConstants.kSteerKVBackRight;
  }

  public GenericEntry getRequestedHeadingEntry() {
    return m_heading;
  }
  public GenericEntry getRequestedDriveVelocityEntry() {
    return m_driveVelocity;
  }
  public GenericEntry getRequestedSteerVelocityEntry() {
    return m_steerVelocity;
  }
  public GenericEntry getRequestedVoltsEntry() {
    return m_drivetrainvolts;
  }
  public GenericEntry getRequestedSteerAngleEntry() {
    return m_steerAngle;
  }
  public GenericEntry getDriveStaticFeedforwardEntry() {
    return m_driveStaticFeedforward;
  }
  public GenericEntry getDriveVelocityFeedforwardEntry() {
    return m_driveVelocityFeedforward;
  }
  public void setDriveModuleFeedforward() {
    //revert to previous saved feed forward data if changed
    
    if (m_prevModule != m_moduleChooser.getSelected()){
      m_driveStaticFeedforward.setDouble(m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
      m_driveVelocityFeedforward.setDouble(m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
      m_prevModule = m_moduleChooser.getSelected();
    }
    
    // update saved feedforward data
    m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()] = m_driveStaticFeedforward.getDouble(0);
    m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()] = m_driveVelocityFeedforward.getDouble(0);
    
    //to set all modules to same feedforward values if all
    // if (m_module.getSelected() == m_allModule){
    //   for(int i = 0; i < 4; i++){
    //     m_modules[i].setDriveFeedForwardValues(m_driveStaticFeedForwardSaver.get(m_module.getSelected()), m_driveVelFeedForwardSaver.get(m_module.getSelected()));
    //   }
    // }
    
    //set selected module
    m_moduleChooser.getSelected().setDriveFeedForwardValues(m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()],m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
  }
  public void setSteerModuleFeedforward() {
    //revert to previous saved feed forward data if changed
    
    if (m_prevModule != m_moduleChooser.getSelected()){
      m_steerStaticFeedforward.setDouble(m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
      m_steerVelocityFeedforward.setDouble(m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
      m_prevModule = m_moduleChooser.getSelected();
    }
    
    // update saved feedforward data
    m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()] = m_steerStaticFeedforward.getDouble(0);
    m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()] =m_steerVelocityFeedforward.getDouble(0);
    
    //to set all modules to same feedforward values if all
    // if (m_module.getSelected() == m_allModule){
    //   for(int i = 0; i < 4; i++){
    //     m_modules[i].setDriveFeedForwardValues(m_steerStaticFeedForwardSaver[m_module.getSelected().getModuleType().getID()], m_steerVelFeedForwardSaver[m_module.getSelected().getModuleType().getID()]);
    //   }
    // }
    
    //set selected module
    m_moduleChooser.getSelected().setDriveFeedForwardValues(m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()],m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getModuleType().getID()]);
  }
  public Double[] getDriveStaticFeedforwardArray(){
    return m_driveStaticFeedForwardSaver;
  }
  public Double[] getDriveVelocityFeedforwardArray(){
    return m_driveVelFeedForwardSaver;
  }
  public Double[] getSteerStaticFeedforwardArray(){
    return m_steerStaticFeedForwardSaver;
  }
  public Double[] getSteerVelocityFeedforwardArray(){
    return m_steerVelFeedForwardSaver;
  }
  public SendableChooser<Module> getModuleChooser(){
    return m_moduleChooser;
  }
  public void setUpModuleChooser(){
    m_moduleChooser.addOption("Front Left", m_modules[0]);
    m_moduleChooser.addOption("Front Right", m_modules[1]);
    m_moduleChooser.addOption("Back Left", m_modules[2]);
    m_moduleChooser.addOption("Back Right", m_modules[3]);
    
  }
}