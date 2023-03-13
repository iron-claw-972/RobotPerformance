package frc.robot.commands.scoring.intake;

import java.util.function.BooleanSupplier;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.constants.IntakeConstants;
import frc.robot.subsystems.RollerIntake;
import frc.robot.subsystems.RollerIntake.IntakeMode;
import frc.robot.util.GamePieceType;

public class IntakeGamePiece extends CommandBase {

  private final RollerIntake m_intake; 
  private GamePieceType m_type;
  private final BooleanSupplier m_isCone;
  private final Timer m_timer;

  /**
   * Spins the intake until the game piece is inside the intake.
   * @param intake the intake subsystem
   * @param isCone a supplier that when the command starts, checks if will intake a cone or cube
   */
  public IntakeGamePiece(RollerIntake intake, BooleanSupplier isCone) {
    m_intake = intake;
    m_isCone = isCone;
    m_timer = new Timer();
    addRequirements(m_intake);
  }

  /**
   * Spins the intake until the game piece is inside the intake.
   * @param intake the intake subsystem
   * @param type the type of game piece to intake
   */
  public IntakeGamePiece(RollerIntake intake, GamePieceType type) {
    m_intake = intake; 
    m_type = type;
    m_isCone = m_type == GamePieceType.CONE ? () -> true : () -> false;
    m_timer = new Timer();
    addRequirements(m_intake);
  }

  @Override
  public void initialize() {
    m_timer.reset();
    if (m_type == null) {
      m_type = m_isCone.getAsBoolean() ? GamePieceType.CONE : GamePieceType.CUBE;
    }
    if (m_type == GamePieceType.CUBE) {
      m_intake.setMode(IntakeMode.INTAKE_CUBE);
    } else if (m_type == GamePieceType.CONE) {
      m_intake.setMode(IntakeMode.INTAKE_CONE);
    }
  }

  @Override
  public void end(boolean interrupted) {
    m_intake.setHeldGamePiece(m_type);
    m_intake.setMode(IntakeMode.DISABLED);
  }
  
  @Override
  public boolean isFinished() {
    if (!m_timer.hasElapsed(IntakeConstants.kIntakeTime)) return false;
    if (m_type == GamePieceType.CUBE) {
      return Math.abs(m_intake.getCurrent()) >= IntakeConstants.kCubeIntakeCurrentStopPoint;
    } else if (m_type == GamePieceType.CONE) {
      return Math.abs(m_intake.getCurrent()) >= IntakeConstants.kConeIntakeCurrentStopPoint;
    }
    return false;
  }

}
