/*----------------------------------------------------------------------------*/
/* Copyright (c) 2018-2019 FIRST. All Rights Reserved.                        */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package frc.robot.subsystems;
import java.util.logging.LogManager;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.LimitSwitchNormal;
import com.ctre.phoenix.motorcontrol.LimitSwitchSource;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ElevatorFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.DutyCycleEncoder;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.constants.Constants;
import frc.robot.constants.ElevatorConstants;
import frc.robot.util.MotorFactory;
import lib.ctre_shims.TalonEncoder;

public class Elevator extends SubsystemBase {

  private final WPI_TalonFX m_motor;
  private final PIDController m_elevatorPID;
  private final ElevatorFeedforward m_elevatorFF;
  private final TalonEncoder m_talonEncoder; 
  private final DigitalInput m_topLimitSwitch; 
  private final DigitalInput m_bottomLimitSwitch; 
  private final ShuffleboardTab m_elevatorTab; 
  private double m_maxHeight; 
  private boolean m_enabled; 
  private boolean m_isCalibrated = false; 

  public Elevator(ShuffleboardTab elevatorTab) {
    m_elevatorTab = elevatorTab; 
    m_motor = MotorFactory.createTalonFX(ElevatorConstants.kMotorPort, Constants.kRioCAN);
    m_motor.setInverted(true);
    m_motor.setNeutralMode(NeutralMode.Brake);
    m_talonEncoder = new TalonEncoder(m_motor); 
    m_talonEncoder.setDistancePerPulse(ElevatorConstants.kDistPerPulse);
    addChild("motor", m_motor);
   
    m_elevatorPID = new PIDController(ElevatorConstants.kP, ElevatorConstants.kI, ElevatorConstants.kD);
    //m_elevatorTab.add("pid",m_elevatorPID);
    //m_elevatorTab.addNumber("",()-> getHeight());
    m_elevatorPID.setTolerance(0.03);
    m_elevatorFF = new ElevatorFeedforward(ElevatorConstants.kS, ElevatorConstants.kG, ElevatorConstants.kV, ElevatorConstants.kA);

    m_topLimitSwitch = new DigitalInput(ElevatorConstants.kTopLimitSwitchPort); 
    m_bottomLimitSwitch = new DigitalInput(ElevatorConstants.kBottomLimitSwitchPort); 
    addChild("Upper Limit",m_topLimitSwitch);
    addChild("Bottom Limit",m_bottomLimitSwitch);


    //TODO: log, addDouble doesn't work. 
    //LogManager.addDouble("Elevator/error", () -> {return m_pid.getSetpoint() - getElevatorHeight();});
    
    //m_motor.setSafetyEnabled(true);
    setUpElevatorTab();
  }

  @Override
  public void periodic() {
    /**
     * If we hit the bottom limit switch, then we must be at the zero position.
     * Thus set the setpoint to 0, then atSetpoint() will be triggered, causing the 
     * ResetEncoderAtBottom() command to end
     */
    if(m_enabled && m_isCalibrated) {
      double pid = m_elevatorPID.calculate(getHeight());
      double ff = m_elevatorFF.calculate(ElevatorConstants.kVelocity, ElevatorConstants.kAccel); 
      ff = 0; 
      double pidClamped = MathUtil.clamp(pid, -ElevatorConstants.kPowerLimit, ElevatorConstants.kPowerLimit);
      double finalMotorPower = pidClamped + ff; 
      
      set(finalMotorPower);
    } else {
      //m_motor.feed();
    }

  }

  public void close() {
    // close the ports
    //TODO: Is there any way to close the ports of the limit switches attached to the motor? 
    m_topLimitSwitch.close(); 
    m_bottomLimitSwitch.close(); 
  }

  public void set(double power) {
    if(isBottom()){
      if(power >0 ){ 
        m_motor.set(power); 
      }else{
        m_motor.set(0); 
      }
    }

    else if(isTop()){
      if(power<0){
        m_motor.set(power); 
      }else{
        m_motor.set(0); 
      }
    }

    else{
      m_motor.set(power); 
    }

  
  }
  
  public void setEnabled(boolean isEnabled){
    m_enabled = isEnabled; 
  }

  public void setIsCalibrated(){
    m_isCalibrated = true; 
  }


  public void setSetpoint(double setpoint){
    m_elevatorPID.setSetpoint(setpoint);
  }

  // public boolean getBottomLimitSwitch(){
  //  if(m_motor.getSensorCollection().isRevLimitSwitchClosed() == 0){
  //   return true; 
  //  }
  //   return false; 
  // }

  // public boolean getTopLimitSwitch(){
  //   if(m_motor.getSensorCollection().isFwdLimitSwitchClosed() == 0){
  //    return true; 
  //   }
  //    return false; 
  // }

  public boolean isBottom(){
    if(m_bottomLimitSwitch.get() == false){
      return true; 
    } else{
      return false; 
    }
  }

  public boolean isTop(){
    if(m_topLimitSwitch.get() == false){
      return true; 
    } else{
      return false; 
    }  }

  public void resetTalonEncoder(){
    m_talonEncoder.reset(); 
  }
 
  /**
   * Determine the elevator height from the motor encoder.
   * @return return height in meters
   */
  public double getHeight() {
    return m_talonEncoder.getDistance(); 
  }

  /**
   * 
   * Zero position is the value of the absolute encoder after the elevator
   * hits the bottom limit switch. 
   * @return return the absolute encoder's zero position in ticks. 
   * 
   */
  public boolean atSetpoint() {
    return m_elevatorPID.atSetpoint();
  }

  public void setMotorPower(double power){
    m_motor.set(power);
  }

  public double setMaxHeight(){
    m_maxHeight = m_talonEncoder.getDistance();
    return m_maxHeight;  
  }

  public void setUpElevatorTab(){
    m_elevatorTab.addNumber("Elevator Height", ()->getHeight());
    m_elevatorTab.add(m_elevatorPID); 
    m_elevatorTab.addBoolean("enabled", ()->m_enabled);
    
  }

}

