package frc.robot.commands.scoring.intake;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.constants.IntakeConstants;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Intake.IntakeMode;
import frc.robot.subsystems.Intake.IntakeObject;

public class OuttakeGamePiece extends CommandBase {

  private final Intake m_intake; 
  private final Timer m_timer;

  /**
   * Spins the intake until the game piece is inside the intake.
   * @param intake the intake subsystem
   */
  public OuttakeGamePiece(Intake intake) {
    m_intake = intake; 
    m_timer = new Timer();
    addRequirements(m_intake);
  }

  @Override
  public void initialize() {
    if (m_intake.getObject() == IntakeObject.CUBE) {
      m_intake.setIntakeMode(IntakeMode.OUTTAKE_CUBE);
    } else if (m_intake.getObject() == IntakeObject.CONE) {
      m_intake.setIntakeMode(IntakeMode.OUTTAKE_CONE);
    }
    m_timer.start();
  }

  @Override
  public void end(boolean interrupted) {
    m_intake.setObject(IntakeObject.NONE);
    m_intake.setIntakeMode(IntakeMode.DISABLED);
  }
  
  @Override
  public boolean isFinished() {
    if (m_timer.advanceIfElapsed(IntakeConstants.kOuttakeTime)) {
      return true;
    }
    return false;
  }

}
