package frc.robot.commands.auto;

import edu.wpi.first.wpilibj2.command.SequentialCommandGroup;
import frc.robot.commands.GoToPose;
import frc.robot.commands.arm.ExtendToPosition;
import frc.robot.subsystems.Drivetrain;
import frc.robot.subsystems.FourBarArm;
import frc.robot.util.PathGroupLoader;

public class EngageBottomPath extends SequentialCommandGroup{
   
    private Drivetrain m_drive;
    private FourBarArm m_arm;

    private double armSetpoint = Math.PI/6;

    public EngageBottomPath(Drivetrain drive, GoToPose goTo, FourBarArm arm)  {
        m_drive = drive;
        m_arm = arm;

        addCommands(
            new ExtendToPosition(m_arm, armSetpoint), //deposit
            new PathPlannerCommand(PathGroupLoader.getPathGroup("BottomSimpleLine1"), 0 , m_drive, true), //intake
            new PathPlannerCommand(PathGroupLoader.getPathGroup("BottomSimpleLine2"), 0 , m_drive, false) //engage
    
        );
    }
}

