package frc.robot.commands.intake;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.constants.IntakeConstants;
import frc.robot.subsystems.Intake;

public class OuttakeGamePiece extends CommandBase {
  Intake m_intake; 
  boolean m_rotate = false;

  /**
   * Spins the intake to outtake until the game piece is ejected.
   * @param intake the intake subsystem
   */
  public OuttakeGamePiece(Intake intake) {
    this(intake, false);
  }

  /**
   * Spins the intake to outtake until the game piece is ejected.
   * @param intake the intake subsystem
   * @param rotate if true, will eject the game piece by spinning the intake wheels in opposite directions
   */
  public OuttakeGamePiece(Intake intake, boolean rotate) {
    m_intake = intake; 
    m_rotate = rotate;
    addRequirements(m_intake);
  }

  @Override
  public void initialize() {
    if (m_rotate) {
      m_intake.spinOuttake(IntakeConstants.kOuttakeSpeed);    
    } else {
      m_intake.intake(IntakeConstants.kOuttakeSpeed);
    }
  }

  @Override
  public void end(boolean interrupted) {
    m_intake.stopIntake(); 
  }
  
  // Returns true when the command should end.
  @Override
  public boolean isFinished() {
    return !m_intake.containsGamePiece(); 
  }

}
