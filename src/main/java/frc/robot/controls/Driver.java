package frc.robot.controls;
import frc.robot.Robot;
import frc.robot.constants.Constants;
import frc.robot.util.TestType;
import lib.controllers.GameController;
import lib.controllers.GameController.Button;

public class Driver {
  private static GameController driver = new GameController(Constants.oi.kDriverJoy);

  public static void configureControls() {
  }
}