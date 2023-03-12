package frc.robot.commands.scoring.intake;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.constants.IntakeConstants;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Intake.IntakeMode;
import frc.robot.subsystems.Intake.IntakeObject;

public class IntakeGamePiece extends CommandBase {

  private final Intake m_intake; 
  private final IntakeObject m_type;

  /**
   * Spins the intake until the game piece is inside the intake.
   * @param intake the intake subsystem
   */
  public IntakeGamePiece(Intake intake, IntakeObject type) {
    m_intake = intake; 
    m_type = type;
    addRequirements(m_intake);
  }

  @Override
  public void initialize() {
    if (m_type == IntakeObject.CUBE) {
      m_intake.setIntakeMode(IntakeMode.INTAKE_CUBE);
    } else if (m_type == IntakeObject.CONE) {
      m_intake.setIntakeMode(IntakeMode.INTAKE_CONE);
    }
  }

  @Override
  public void end(boolean interrupted) {
    m_intake.setObject(m_type);
    m_intake.setIntakeMode(IntakeMode.DISABLED);
  }
  
  @Override
  public boolean isFinished() {
    if (m_intake.getIntakeMotor().getStatorCurrent() >= IntakeConstants.kIntakeCurrentStopPoint) {
      return true;
    }
    return false;
  }

}
