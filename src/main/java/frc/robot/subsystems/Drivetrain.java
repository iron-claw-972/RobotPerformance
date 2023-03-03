package frc.robot.subsystems;

import java.util.ArrayList;
import java.util.function.DoubleSupplier;

import org.photonvision.EstimatedRobotPose;

import com.ctre.phoenix.sensors.WPI_Pigeon2;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.Pair;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.GenericEntry;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.constants.Constants;
import frc.robot.constants.VisionConstants;
import frc.robot.constants.swerve.DriveConstants;
import frc.robot.constants.swerve.ModuleConstants;
import frc.robot.util.LogManager;
import frc.robot.util.Vision;

/** 
 * Represents a swerve drive style drivetrain.
 * 
 * Module IDs are:
 * 1: Front left
 * 2: Front right
 * 3: Back left
 * 4: Back right
 */
public class Drivetrain extends SubsystemBase {

  public Vision m_vision;

  // This is left intentionally public
  public final Module[] m_modules;
  
  private final SwerveDriveKinematics m_kinematics = new SwerveDriveKinematics(
    new Translation2d(DriveConstants.kTrackWidth / 2, DriveConstants.kTrackWidth / 2),
    new Translation2d(DriveConstants.kTrackWidth / 2, -DriveConstants.kTrackWidth / 2),
    new Translation2d(-DriveConstants.kTrackWidth / 2, DriveConstants.kTrackWidth / 2),
    new Translation2d(-DriveConstants.kTrackWidth / 2, -DriveConstants.kTrackWidth / 2)
  );

  // Pigeon
  private final WPI_Pigeon2 m_pigeon = new WPI_Pigeon2(DriveConstants.kPigeon, DriveConstants.kPigeonCAN);
  private boolean m_hasResetYaw = false; // the initial yaw has been set 

  private double m_headingPIDOutput = 0;

  // Odometry
  private final SwerveDrivePoseEstimator m_poseEstimator;

  // If the robot is driving over the charge station in auto
  private boolean m_chargeStationAuto = false;

  // Displays the field with the robots estimated pose on it
  private final Field2d m_fieldDisplay = new Field2d();
  
  // PID Controllers
  // translation controllers have dummy constants that are just good enough to run the odometry test
  private final PIDController m_xController = new PIDController(0.1, 0, 0);
  private final PIDController m_yController = new PIDController(0.1, 0, 0);
  private final PIDController m_rotationController = new PIDController(
    DriveConstants.kHeadingP, 
    DriveConstants.kHeadingI, 
    DriveConstants.kHeadingD
  );

  //Shuffleboard
  private GenericEntry 
    m_driveVelocityEntry,
    m_steerVelocityEntry, 
    m_steerAngleEntry, 
    m_drivetrainVoltsEntry, 
    m_driveStaticFeedforwardEntry, 
    m_driveVelocityFeedforwardEntry, 
    m_steerStaticFeedforwardEntry,
    m_steerVelocityFeedforwardEntry,
    m_headingEntry;
  private ShuffleboardTab m_swerveModulesTab, m_drivetrainTab;

  private Double[] m_driveVelFeedForwardSaver = new Double[4];
  private Double[] m_driveStaticFeedForwardSaver = new Double[4];
  private Double[] m_steerVelFeedForwardSaver = new Double[4];
  private Double[] m_steerStaticFeedForwardSaver = new Double[4];
  
  private SendableChooser<Module> m_moduleChooser = new SendableChooser<>();
  // modules needed to distinguish in chooser
  private Module m_prevModule;

  boolean m_visionEnabled = true;

  /**
   * Creates a new Swerve Style Drivetrain.
   * @param drivetrainTab the shuffleboard tab to display drivetrain data on
   * @param swerveModulesTab the shuffleboard tab to display module data on
   */
  public Drivetrain(ShuffleboardTab drivetrainTab, ShuffleboardTab swerveModulesTab, Vision vision) {

    LiveWindow.disableAllTelemetry();
    m_drivetrainTab = drivetrainTab;
    m_swerveModulesTab = swerveModulesTab;

    m_vision = vision;
    
    m_modules = new Module[] {
      Module.create(ModuleConstants.FRONT_LEFT, m_swerveModulesTab),
      Module.create(ModuleConstants.FRONT_RIGHT, m_swerveModulesTab),
      Module.create(ModuleConstants.BACK_LEFT, m_swerveModulesTab),
      Module.create(ModuleConstants.BACK_RIGHT, m_swerveModulesTab)
    };
    m_prevModule = m_modules[0];
    
    m_poseEstimator = new SwerveDrivePoseEstimator(
      m_kinematics,
      m_pigeon.getRotation2d(),
      getModulePositions(),
      new Pose2d()
    );
    m_poseEstimator.setVisionMeasurementStdDevs(VisionConstants.kBaseVisionPoseStdDevs);

    m_rotationController.enableContinuousInput(-Math.PI, Math.PI);
    
    m_fieldDisplay.setRobotPose(getPose());
    SmartDashboard.putData("Field", m_fieldDisplay);
  }

  @Override
  public void periodic() {
    updateDriveModuleFeedforwardShuffleboard();
    updateDriveModuleFeedforwardShuffleboard();

    updateOdometry();
    
    m_fieldDisplay.setRobotPose(getPose());

    if (Constants.kLogging) updateLogs();
  }

  /**
   * @return chassis speed of swerve drive
   */
  public ChassisSpeeds getChassisSpeeds() {
    return m_kinematics.toChassisSpeeds(
      m_modules[0].getState(),
      m_modules[1].getState(),
      m_modules[2].getState(),
      m_modules[3].getState()
    );
  }

  /**
   * @return velocity of swerve drive as <magnitude, direction>
   */
  public Pair<Double, Double> getVelocity() {
    ChassisSpeeds chassisSpeeds = getChassisSpeeds();
    return new Pair<>(
      Math.hypot(chassisSpeeds.vxMetersPerSecond, chassisSpeeds.vyMetersPerSecond),
      Math.atan2(chassisSpeeds.vyMetersPerSecond, chassisSpeeds.vxMetersPerSecond)
    );
  }
  
  /**
   * 
   * Resets the pigeon IMU's yaw.
   * 
   * @param degrees the new yaw angle, in degrees.
   */
  public void setPigeonYaw(double degrees) {
    m_pigeon.setYaw(degrees);
  }
  
  /**
  * Resets the pigeon yaw, but only if it hasn't already been reset. Will reset it to {@link DriveConstants.kStartingHeadingDegrees}
  *
  * @param force Will reset the yaw no matter what
  */
  public void initializePigeonYaw(boolean force) {
    if (!m_hasResetYaw || force) {
      m_hasResetYaw = true;
      // TODO: reset the yaw to different angles depending on auto start position
      setPigeonYaw(DriveConstants.kStartingHeadingDegrees);
    }
  }
  
  /**
  * Method to drive the robot using joystick info.
  *
  * @param xSpeed speed of the robot in the x direction (forward) in m/s
  * @param ySpeed speed of the robot in the y direction (sideways) in m/s
  * @param rot angular rate of the robot in rad/s
  * @param fieldRelative whether the provided x and y speeds are relative to the field
  */
  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative) {           
    setChassisSpeeds((
      fieldRelative
          ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, getRotation2d())
          : new ChassisSpeeds(xSpeed, ySpeed, rot)
      )
    );
  }  

  /**
   * Drives the robot using the provided x speed, y speed, and positional heading.
   * 
   * @param xSpeed speed of the robot in the x direction (forward)
   * @param ySpeed speed of the robot in the y direction (sideways)
   * @param heading target heading of the robot in radians
   * @param fieldRelative whether the provided x and y speeds are relative to the field
   */
  public void driveHeading(double xSpeed, double ySpeed, double heading, boolean fieldRelative) {
    m_headingPIDOutput = m_rotationController.calculate(getAngleHeading(), heading);
    double rot = m_headingPIDOutput;
    setChassisSpeeds((
      fieldRelative
          ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, m_pigeon.getRotation2d())
          : new ChassisSpeeds(xSpeed, ySpeed, rot)
      )
    );
  }

  /**
   * Sets the chassis speeds of the robot.
   * 
   * @param chassisSpeeds the target chassis speeds
   */
  public void setChassisSpeeds(ChassisSpeeds chassisSpeeds) {
    SwerveModuleState[] swerveModuleStates = m_kinematics.toSwerveModuleStates(chassisSpeeds);
    SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, DriveConstants.kMaxSpeed);
    setModuleStates(swerveModuleStates);
  }

  /**
   * Runs the PID controllers with the provided x, y, and rot values. Then, calls {@link #drive()} using the PID outputs.
   * This is based on the odometry of the chassis.
   * 
   * @param x the position to move to in the x, in meters
   * @param y the position to move to in the y, in meters
   * @param rot the angle to move to, in radians
   */
  public void runChassisPID(double x, double y, double rot) {
    double xSpeed = m_xController.calculate(m_poseEstimator.getEstimatedPosition().getX(), x);
    double ySpeed = m_yController.calculate(m_poseEstimator.getEstimatedPosition().getY(), y);
    double rotRadians = m_rotationController.calculate(getAngleHeading(), rot);
    drive(xSpeed, ySpeed, rotRadians, true);
  }
  
  /** Updates the field relative position of the robot. */
  public void updateOdometry() {
    // Updates pose based on encoders and gyro
    m_poseEstimator.update(
      m_pigeon.getRotation2d(),
      getModulePositions()
    );

    // Updates pose based on vision
    if (m_visionEnabled){
      //TODO: there should be a cleaner way to prosses vision

      // An array list of poses returned by different cameras
      ArrayList<EstimatedRobotPose> estimatedPoses = m_vision.getEstimatedPoses(m_poseEstimator.getEstimatedPosition());
      // The current position as a translation
      Translation2d currentEstimatedPoseTranslation = m_poseEstimator.getEstimatedPosition().getTranslation();
      for (int i = 0; i < estimatedPoses.size(); i++) {
        EstimatedRobotPose estimatedPose = estimatedPoses.get(i);
        // The position of the closest april tag as a translation
        Translation2d closestTagPoseTranslation = new Translation2d();
        for (int j = 0; j < estimatedPose.targetsUsed.size(); j++) {
          // The position of the current april tag
          Pose3d currentTagPose = m_vision.getTagPose(estimatedPose.targetsUsed.get(j).getFiducialId());
          // If it can't find the april tag's pose, don't run the rest of the for loop for this tag
          if(currentTagPose == null){
            continue;
          }
          Translation2d currentTagPoseTranslation = currentTagPose.toPose2d().getTranslation();
          
          // If the current april tag position is closer than the closest one, this makes makes it the closest
          if (j == 0 || currentEstimatedPoseTranslation.getDistance(currentTagPoseTranslation) < currentEstimatedPoseTranslation.getDistance(closestTagPoseTranslation)) {
            closestTagPoseTranslation = currentTagPoseTranslation;
          }
        }
        // Adds the vision measurement for this camera
        if(m_chargeStationAuto){
          m_poseEstimator.addVisionMeasurement(
            estimatedPose.estimatedPose.toPose2d(),
            estimatedPose.timestampSeconds,
            VisionConstants.kChargeStationVisionPoseStdDevs
          );
        }else{
          m_poseEstimator.addVisionMeasurement(
            estimatedPose.estimatedPose.toPose2d(),
            estimatedPose.timestampSeconds,
            VisionConstants.kBaseVisionPoseStdDevs.plus(
              currentEstimatedPoseTranslation.getDistance(closestTagPoseTranslation) * VisionConstants.kVisionPoseStdDevFactor
            )
          );
        }
      }

    }
  }
  
  /**
  * Returns the angular rate from the pigeon.
  * 
  * @param id 0 for x, 1 for y, 2 for z
  * @return the rate in rads/s from the pigeon
  */
  public double getAngularRate(int id) {

    // uses pass by reference and edits reference to array
    double[] rawGyros = new double[3];
    m_pigeon.getRawGyro(rawGyros);

    // outputs in deg/s, so convert to rad/s
    return Units.degreesToRadians(rawGyros[id]);
  }
  
  /**
  * Gets the current robot pose from the odometry.
  */
  public Pose2d getPose() {
    return m_poseEstimator.getEstimatedPosition();
  }
  
  /**
  * Resets the odometry to the given pose.
  * @param pose the pose to reset to.
  */
  public void resetOdometry(Pose2d pose) {
    m_poseEstimator.resetPosition(getRotation2d(), getModulePositions(), pose);
  }
  
  /**
  * @return the pigeon's heading in a Rotation2d
  */
  public Rotation2d getRotation2d() {
    return m_pigeon.getRotation2d(); 
  }
  
  /**
  * Gets the angle heading from the pigeon.
  * 
  * @return the heading angle in radians, from -pi to pi
  */
  public double getAngleHeading() {
    return MathUtil.angleModulus(m_pigeon.getRotation2d().getRadians());
  }
  
  /**
  * Gets an array of all the swerve module positions.
  * 
  * @return an array of all swerve module positions
  */
  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[] {
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
  
  /**
   * Enables or disables the state deadband for all swerve modules. 
   * The state deadband determines if the robot will stop drive and steer motors when inputted drive velocity is low. 
   * It should be enabled for all regular driving, to prevent releasing the controls from setting the angles.
   */
  public void enableStateDeadband(boolean stateDeadBand){
    for (int i = 0; i < 4; i++) {
      m_modules[i].enableStateDeadband(stateDeadBand);
    }
  }

  /**
   * Sets the optimize state for all swerve modules.
   * Optimizing the state means the modules will not turn the steer motors more than 90 degrees for any one movement.
   */
  public void setAllOptimize(Boolean optimizeSate) {
    for (int i = 0; i < 4; i++) {
      m_modules[i].setOptimize(optimizeSate);
    }
  }

  /**
   * If this is set to true, it will trust vision more
   * This is for if the robot moves over the charge station, which messes up odometry
  */
  public void setChargeStationAuto(boolean chargeStationAuto){
    m_chargeStationAuto=chargeStationAuto;
  }
  
  /**
   * Stops all swerve modules.
   */
  public void stop() {
    for (int i = 0; i < 4; i++) {
      m_modules[i].stop();
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
  public SwerveDriveKinematics getKinematics() {
    return m_kinematics;
  }

  /**
   * Sets up the shuffleboard tab for the drivetrain.
   */
  public void setupDrivetrainShuffleboard() {
    // inputs
    m_headingEntry = m_drivetrainTab.add("Set Heading (-pi to pi)", 0).getEntry();
    
    // add PID controllers
    m_drivetrainTab.add("xController", getXController());
    m_drivetrainTab.add("yController", getYController());
    m_drivetrainTab.add("rotationController", getRotationController());
    
    // add angles
    m_drivetrainTab.addNumber("getAngle", () -> getAngleHeading());
    m_drivetrainTab.addNumber("heading PID output", () -> m_headingPIDOutput);
    
    m_drivetrainTab.addNumber("Gyro X", () -> getAngularRate(0));
    m_drivetrainTab.addNumber("Gyro Y", () -> getAngularRate(1));
    m_drivetrainTab.addNumber("Gyro Z", () -> getAngularRate(2));
    
    // m_drivetrainTab.add("odometry", m_odometry);
    
    // add the controllers to shuffleboard for tuning
    m_drivetrainTab.add(getXController());
    m_drivetrainTab.add(getYController());
    m_drivetrainTab.add(getRotationController());
  }

  /**
   * Sets up the shuffleboard tab for the swerve modules.
   */
  public void setupModulesShuffleboard() {
    setUpModuleChooser();
    setUpFeedforwardSavers();
    
    // inputs
    m_swerveModulesTab.add("Module Chooser", m_moduleChooser);
    m_driveVelocityEntry = m_swerveModulesTab.add("Set Drive Velocity", 0).getEntry();
    m_steerVelocityEntry = m_swerveModulesTab.add("Set Steer Velocity", 0).getEntry();
    m_steerAngleEntry = m_swerveModulesTab.add("Set Steer Angle", 0).getEntry();
    m_drivetrainVoltsEntry = m_swerveModulesTab.add("Set Volts", 0).getEntry();
    m_driveStaticFeedforwardEntry = m_swerveModulesTab.add(
      "Drive kS FF", 
      m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    ).getEntry();

    m_driveVelocityFeedforwardEntry = m_swerveModulesTab.add(
      "Drive kV FF", 
      m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    ).getEntry();

    m_steerStaticFeedforwardEntry = m_swerveModulesTab.add(
      "Steer kS FF", 
      m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    ).getEntry();

    m_steerVelocityFeedforwardEntry = m_swerveModulesTab.add(
      "Steer kV FF", 
      m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    ).getEntry();

    
    for (int i = 0; i < 4; i++) {
      m_modules[i].setupModulesShuffleboard();
    }
  }

  public GenericEntry getRequestedHeadingEntry() {
    return m_headingEntry;
  }
  public GenericEntry getRequestedDriveVelocityEntry() {
    return m_driveVelocityEntry;
  }
  public GenericEntry getRequestedSteerVelocityEntry() {
    return m_steerVelocityEntry;
  }
  public GenericEntry getRequestedVoltsEntry() {
    return m_drivetrainVoltsEntry;
  }
  public GenericEntry getRequestedSteerAngleEntry() {
    return m_steerAngleEntry;
  }
  public GenericEntry getDriveStaticFeedforwardEntry() {
    return m_driveStaticFeedforwardEntry;
  }
  public GenericEntry getDriveVelocityFeedforwardEntry() {
    return m_driveVelocityFeedforwardEntry;
  }
  public GenericEntry getSteerStaticFeedforwardEntry() {
    return m_steerStaticFeedforwardEntry;
  }
  public GenericEntry getSteerVelocityFeedforwardEntry() {
    return m_steerVelocityFeedforwardEntry;
  }

  /**
   * Updates the drive module feedforward values on shuffleboard.
   */
  public void updateDriveModuleFeedforwardShuffleboard() {
    // revert to previous saved feed forward data if changed
    if (m_prevModule != m_moduleChooser.getSelected()) {
      m_driveStaticFeedforwardEntry.setDouble(
        m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()]
      );
      m_driveVelocityFeedforwardEntry.setDouble(
        m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
      );
      m_prevModule = m_moduleChooser.getSelected();
    }
    
    // update saved feedforward data
    m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()] = 
      m_driveStaticFeedforwardEntry.getDouble(0);
    m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getId()] = 
      m_driveVelocityFeedforwardEntry.getDouble(0);
    
    // to set all modules to same feedforward values if all
    // if (m_module.getSelected() == m_allModule) {
    //   for(int i = 0; i < 4; i++) {
    //     m_modules[i].setDriveFeedForwardValues(m_driveStaticFeedForwardSaver.get(m_module.getSelected()), m_driveVelFeedForwardSaver.get(m_module.getSelected()));
    //   }
    // }
        
    //set selected module
    m_moduleChooser.getSelected().setDriveFeedForwardValues(
      m_driveStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()],
      m_driveVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    );
  }

  /**
   * Updates the steer module feedforward values on shuffleboard.
   */
  public void updateSteerModuleFeedforwardShuffleboard() {
    
    //revert to previous saved feed forward data if changed
    if (m_prevModule != m_moduleChooser.getSelected()) {
      m_steerStaticFeedforwardEntry.setDouble(
        m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()]
      );
      m_steerVelocityFeedforwardEntry.setDouble(
        m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
      );
      m_prevModule = m_moduleChooser.getSelected();
    }
    
    // update saved feedforward data
    m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()] = 
      m_steerStaticFeedforwardEntry.getDouble(0);
    m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getId()] = 
      m_steerVelocityFeedforwardEntry.getDouble(0);
    
    //to set all modules to same feedforward values if all
    // if (m_module.getSelected() == m_allModule) {
    //   for(int i = 0; i < 4; i++) {
    //     m_modules[i].setDriveFeedForwardValues(m_steerStaticFeedForwardSaver[m_module.getSelected().getId()], m_steerVelFeedForwardSaver[m_module.getSelected().getId()]);
    //   }
    // }
    
    //set selected module
    m_moduleChooser.getSelected().setDriveFeedForwardValues(
      m_steerStaticFeedForwardSaver[m_moduleChooser.getSelected().getId()],
      m_steerVelFeedForwardSaver[m_moduleChooser.getSelected().getId()]
    );
  }
  
  /**
   * Sets up feedforward savers.
   */
  private void setUpFeedforwardSavers() {
    m_driveStaticFeedForwardSaver = new Double[] {
      m_modules[0].getDriveFeedForwardKS(),
      m_modules[1].getDriveFeedForwardKS(),
      m_modules[2].getDriveFeedForwardKS(),
      m_modules[3].getDriveFeedForwardKS()
    };
    m_driveVelFeedForwardSaver = new Double[] {
      m_modules[0].getDriveFeedForwardKV(),
      m_modules[1].getDriveFeedForwardKV(),
      m_modules[2].getDriveFeedForwardKV(),
      m_modules[3].getDriveFeedForwardKV()
    };
    m_steerStaticFeedForwardSaver = new Double[] {
      m_modules[0].getSteerFeedForwardKS(),
      m_modules[1].getSteerFeedForwardKS(),
      m_modules[2].getSteerFeedForwardKS(),
      m_modules[3].getSteerFeedForwardKS()
    };
    m_steerVelFeedForwardSaver = new Double[] {
      m_modules[0].getSteerFeedForwardKV(),
      m_modules[1].getSteerFeedForwardKV(),
      m_modules[2].getSteerFeedForwardKV(),
      m_modules[3].getSteerFeedForwardKV()
    };
  }
  
  public Double[] getDriveStaticFeedforwardArray() {
    return m_driveStaticFeedForwardSaver;
  }
  public Double[] getDriveVelocityFeedforwardArray() {
    return m_driveVelFeedForwardSaver;
  }
  public Double[] getSteerStaticFeedforwardArray() {
    return m_steerStaticFeedForwardSaver;
  }
  public Double[] getSteerVelocityFeedforwardArray() {
    return m_steerVelFeedForwardSaver;
  }

  public SendableChooser<Module> getModuleChooser() {
    return m_moduleChooser;
  }

  /**
   * Sets up module chooser.
   */
  public void setUpModuleChooser() {
    m_moduleChooser.setDefaultOption("Front Left", m_modules[0]);
    m_moduleChooser.addOption("Front Right", m_modules[1]);
    m_moduleChooser.addOption("Back Left", m_modules[2]);
    m_moduleChooser.addOption("Back Right", m_modules[3]);
  }

  public void enableVision(boolean enabled) {
     m_visionEnabled = enabled;
  }

  public void updateLogs(){
    double[] pose = {
      getPose().getX(),
      getPose().getY(),
      getPose().getRotation().getRadians()
    };
    LogManager.addDoubleArray("Swerve/Pose2d", pose);
    double[] actualStates = {
      m_modules[0].getSteerAngle(),
      m_modules[0].getDriveVelocity(),
      m_modules[1].getSteerAngle(),
      m_modules[1].getDriveVelocity(),
      m_modules[2].getSteerAngle(),
      m_modules[2].getDriveVelocity(),
      m_modules[3].getSteerAngle(),
      m_modules[3].getDriveVelocity()
    };
    LogManager.addDoubleArray("Swerve/actual swerve states", actualStates);
    double[] desiredStates = {
      m_modules[0].getSteerAngle(),
      m_modules[0].getDriveVelocity(),
      m_modules[1].getSteerAngle(),
      m_modules[1].getDriveVelocity(),
      m_modules[2].getSteerAngle(),
      m_modules[2].getDriveVelocity(),
      m_modules[3].getSteerAngle(),
      m_modules[3].getDriveVelocity()
    };
    LogManager.addDoubleArray("Swerve/desired swerve states", desiredStates);
    for (int i = 0; i < 4; i++){
      m_modules[i].updateLogs();
    }
  }

}