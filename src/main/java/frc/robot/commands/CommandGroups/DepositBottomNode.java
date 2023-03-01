package frc.robot.commands.CommandGroups;
import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.arm.ExtendToPosition;
import frc.robot.commands.elevator.MoveToExtension;
import frc.robot.commands.intake.OuttakeNormal;
import frc.robot.constants.ArmConstants;
import frc.robot.constants.ElevatorConstants;
import frc.robot.subsystems.Elevator;
import frc.robot.subsystems.FourBarArm;
import frc.robot.subsystems.Intake;

public class DepositBottomNode extends SequentialCommandGroup {
  public DepositBottomNode(Elevator elevator, FourBarArm arm, Intake intake) {
    addRequirements(elevator, arm, intake);
    addCommands(
      new MoveToExtension(elevator, ElevatorConstants.kHybridNodeOuttakeExtension),
      new ExtendToPosition(arm, ArmConstants.kBottomNodePositionAbsEncoderPos),
      new OuttakeNormal(intake), 
      new Stow(elevator, arm)
    );
  }
}
