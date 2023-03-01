package frc.robot.commands.intake;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.constants.IntakeConstants;
import frc.robot.subsystems.Intake;

public class OuttakeNormal extends CommandBase {
  Intake m_intake; 

  public OuttakeNormal(Intake intake){
    m_intake = intake; 
    addRequirements(m_intake);
  }

  @Override
  public void initialize() {
    m_intake.intake(-IntakeConstants.kOuttakeSpeed);
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
