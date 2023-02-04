package frc.robot.subsystems;

import edu.wpi.first.math.util.Units;
import com.revrobotics.CANSparkMax;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxAlternateEncoder;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import edu.wpi.first.wpilibj.simulation.EncoderSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.constants.Constants;

public class FourBarArm extends SubsystemBase {
  private final CANSparkMax m_motor;
  private final SparkMaxPIDController m_pid;
  private final RelativeEncoder m_encoder;
  private double armSetpoint = Constants.arm.initialPosition;

  private final SingleJointedArmSim m_armSim = 
    new SingleJointedArmSim(
      Constants.arm.armSimMotor, 
      Constants.arm.armReduction, 
      Constants.arm.armMOI, 
      Constants.arm.armLength, 
      Units.degreesToRadians(0), 
      Units.degreesToRadians(180), 
      true
      );
  private final EncoderSim m_encoderSim = new EncoderSim(m_encoder);
  private double armPositionDeg = 0;
  private double kArmEncoderDistPerPulse = 2.0*Math.PI/8192;

  public FourBarArm() {
    m_motor = new CANSparkMax(Constants.arm.motorID, MotorType.kBrushless);
    m_encoder = m_motor.getAlternateEncoder(SparkMaxAlternateEncoder.Type.kQuadrature, 8192);
    m_pid = m_motor.getPIDController();
    m_pid.setP(Constants.arm.kP);
    m_pid.setI(Constants.arm.kI);
    m_pid.setD(Constants.arm.kD);
    m_pid.setReference(armSetpoint, CANSparkMax.ControlType.kPosition);
    m_motor.setIdleMode(CANSparkMax.IdleMode.kBrake);
  }
  public void setSetpoint(double target) {
    armSetpoint = target;
    m_pid.setReference(armSetpoint, CANSparkMax.ControlType.kPosition);
  }
}
