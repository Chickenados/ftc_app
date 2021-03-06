package chickenados.robotv3;

import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;

import chickenados.robotv1.RobotV1Collector;
import chickenados.robotv1.RobotV1Dropper;
import chickenados.robotv1.RobotV1Grabber;
import chickenados.robotv3.RobotV3Info;
import chickenados.robotv1.RobotV1Lift;
import chickenados.robotv1.RobotV1VisionAnalyzer;
import chickenlib.CknDriveBase;
import chickenlib.CknPIDController;
import chickenlib.CknPIDDrive;
import chickenlib.display.CknSmartDashboard;
import chickenlib.inputstreams.CknEncoderInputStream;
import chickenlib.inputstreams.CknLocationInputStream;
import chickenlib.location.CknLocationTracker;
import chickenlib.opmode.CknRobot;
import chickenlib.sensor.CknAccelerometer;
import chickenlib.sensor.CknBNO055IMU;

public class RobotV3 extends CknRobot {

    HardwareMap hwMap;

    public CknDriveBase driveBase;
    public CknPIDDrive pidDrive;
    public CknLocationTracker locationTracker;

    public CknPIDController yPid;
    public CknPIDController turnPid;
    CknPIDController liftPid;

    CknBNO055IMU imu;

    DcMotor frontLeft;
    DcMotor frontRight;
    DcMotor rearLeft;
    DcMotor rearRight;

    public DcMotor liftMotor;
    public DcMotor pivotMotor;
    public DcMotor reachMotor;


    public DcMotor sliderMotor;
    public DcMotor xrailMotor;
    public DcMotor spinnerMotor;

    public CRServo LCollectorPivot;
    public CRServo RCollectorPivot;

    public DcMotor XRailAuto;

    Servo dropperServo;
    CRServo collectorServo;

    public CknSmartDashboard dashboard;

    //Vuforia Variables
    private static final String TFOD_MODEL_ASSET = "RoverRuckus.tflite";
    private static final String LABEL_GOLD_MINERAL = "Gold Mineral";
    private static final String LABEL_SILVER_MINERAL = "Silver Mineral";
    private boolean useVuforia;
    private boolean useTfod;
    private VuforiaLocalizer vuforia;
    public TFObjectDetector tfod;

    //Subsystems
    public RobotV1Lift lift;
    public RobotV1Grabber grabber;
    public RobotV1VisionAnalyzer analyzer = new RobotV1VisionAnalyzer(LABEL_GOLD_MINERAL);
    public RobotV3MarkerScorer collectorBox;
    public RobotV3XRail xRail;

    public RobotV3(HardwareMap hwMap, Telemetry telemetry){
        this(hwMap, telemetry, false, false);
    }

    public RobotV3(HardwareMap hwMap, Telemetry telemetry, boolean useVuforia){
        this(hwMap, telemetry, useVuforia, false);
    }

    /**
     *
     * @param hwMap         The HardwareMap from the opMode.
     * @param telemetry     Telemetry from the opMode.
     * @param useVuforia    Whether to use Vuforia Trackables Detection.
     * @param useTfod       Whether to use TFOD Object detection.
     */
    public RobotV3(HardwareMap hwMap, Telemetry telemetry, boolean useVuforia, boolean useTfod){

        this.useVuforia = useVuforia;
        this.useTfod = useTfod;
        this.hwMap = hwMap;

        //
        // If specified, Init vuforia/tfod.
        //
        if(useVuforia || useTfod){
            initVuforia();
        }
        if(useTfod){
            if (ClassFactory.getInstance().canCreateTFObjectDetector()) {
                initTfod();
            } else {
                telemetry.addData("Sorry!", "This device is not compatible with TFOD");
            }
        }

        //
        // Initialize sensors
        //

        // Acclerometer Parameters
        CknAccelerometer.Parameters aParameters = new CknAccelerometer.Parameters();
        aParameters.doIntegration = true;

        imu = new CknBNO055IMU(hwMap,"imu", aParameters);

        //
        // Initialize Drive Train system
        //

        frontLeft = hwMap.dcMotor.get(RobotV3Info.FRONT_LEFT_NAME);
        frontRight = hwMap.dcMotor.get(RobotV3Info.FRONT_RIGHT_NAME);
        rearLeft = hwMap.dcMotor.get(RobotV3Info.REAR_LEFT_NAME);
        rearRight = hwMap.dcMotor.get(RobotV3Info.REAR_RIGHT_NAME);

        // Initialize any other motor
        liftMotor = hwMap.dcMotor.get(RobotV3Info.LIFT_MOTOR_NAME);
        liftMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        liftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        sliderMotor = hwMap.dcMotor.get(RobotV3Info.SLIDER_MOTOR_NAME);
        sliderMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        sliderMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sliderMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        spinnerMotor = hwMap.dcMotor.get(RobotV3Info.SPINNER_MOTOR_NAME);
        spinnerMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        spinnerMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        spinnerMotor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        xrailMotor = hwMap.dcMotor.get(RobotV3Info.XRAIL_MOTOR_NAME);
        xrailMotor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        xrailMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);


        LCollectorPivot = hwMap.get(CRServo.class, "scorer");
        LCollectorPivot.setDirection(DcMotorSimple.Direction.REVERSE);
        XRailAuto = hwMap.get(DcMotor.class, "xrail");


        // Reverse Motors
        frontLeft.setDirection(DcMotorSimple.Direction.REVERSE);
        rearLeft.setDirection(DcMotorSimple.Direction.REVERSE);

        //Set motors to braking
        frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rearLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rearRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        CknDriveBase.Parameters params = new CknDriveBase.Parameters();
        params.driveTypes.add(CknDriveBase.DriveType.TANK);
        params.driveTypes.add(CknDriveBase.DriveType.ARCADE);
        params.driveTypes.add(CknDriveBase.DriveType.MECANUM);
        params.ticksPerRev = RobotV3Info.ENCODER_TICKS_PER_REV;
        params.gearRatio = RobotV3Info.GEAR_RATIO;
        params.wheelDiameter = RobotV3Info.WHEEL_DIAMETER_INCHES;

        driveBase = new CknDriveBase(frontLeft, frontRight, rearLeft, rearRight, params);
        driveBase.setMode(CknDriveBase.DriveType.TANK);

        //
        // Location Tracking subsystem
        //

        CknLocationTracker.Parameters LTParams = new CknLocationTracker.Parameters();
        LTParams.useEncoders = true;
        LTParams.useGyro = true;

        locationTracker = new CknLocationTracker(driveBase, imu.gyro, imu.accelerometer, LTParams);
        locationTracker.resetLocation();
        locationTracker.setTaskEnabled(true);

        //
        // Initialize SmartDashboard system
        //
        CknSmartDashboard.Parameters dashParams = new CknSmartDashboard.Parameters();
        dashParams.displayWidth = 400;
        dashParams.numLines = 32;
        dashboard = CknSmartDashboard.createInstance(telemetry, dashParams);

        //
        // PID Drive systems
        //

        CknPIDController.Parameters yParams = new CknPIDController.Parameters();
        yParams.allowOscillation = false;
        yParams.useWraparound = false;

        yPid = new CknPIDController(new CknPIDController.PIDCoefficients(RobotV3Info.Y_ENCODER_PID_P,
                RobotV3Info.Y_ENCODER_PID_I, RobotV3Info.Y_ENCODER_PID_D),
                new CknLocationInputStream(locationTracker, CknLocationInputStream.InputType.Y_POSITION),
                yParams);


        CknPIDController.Parameters turnParams = new CknPIDController.Parameters();
        turnParams.allowOscillation = true;
        turnParams.settlingTimeThreshold = 0.3;
        turnParams.useWraparound = false;
        turnParams.maxTarget = 360;
        turnParams.minTarget = 0;
        turnParams.threshold = 2.0;

        turnPid = new CknPIDController(new CknPIDController.PIDCoefficients(RobotV3Info.TURN_PID_P,
                RobotV3Info.TURN_PID_I, RobotV3Info.TURN_PID_D),
                new CknLocationInputStream(locationTracker, CknLocationInputStream.InputType.HEADING),
                turnParams);

        pidDrive = new CknPIDDrive(driveBase, yPid, turnPid);

        //
        // Lift Subsystems
        //

        CknPIDController.Parameters liftParams = new CknPIDController.Parameters();
        liftParams.allowOscillation = false;
        liftParams.useWraparound = false;

        liftPid = new CknPIDController(new CknPIDController.PIDCoefficients(RobotV3Info.LIFT_PID_P,
                RobotV3Info.LIFT_PID_I, RobotV3Info.LIFT_PID_D),
                new CknEncoderInputStream(liftMotor), liftParams);
        lift = new RobotV1Lift(liftMotor, liftPid);

        //
        // Collector Box Subsystem
        //
        RobotV3MarkerScorer.Parameters boxParams = new RobotV3MarkerScorer.Parameters();
        boxParams.retractTime = RobotV3Info.retractTime;
        boxParams.extendTime = RobotV3Info.extendTime;

        RobotV3XRail.Parameters xrailParams = new RobotV3XRail.Parameters();
        xrailParams.retractTime = RobotV3Info.xrailTimeRetract;
        xrailParams.extendTime = RobotV3Info.xrailTimeExtend;


        collectorBox = new RobotV3MarkerScorer(boxParams, LCollectorPivot);
        xRail= new RobotV3XRail(xrailParams, XRailAuto);

        //
        // Grabber subsystem
        //

        grabber = new RobotV1Grabber(hwMap.get(CRServo.class, "grabber"));

    }

    private void initVuforia(){
        // Init Vuforia
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters();

        parameters.vuforiaLicenseKey = RobotV3Info.VUFORIA_KEY;
        parameters.cameraName = hwMap.get(WebcamName.class, RobotV3Info.WEBCAME_NAME);

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);
        if(useVuforia){
            //TODO: Add vufuria trackables init
        }
    }

    private void initTfod(){
        int tfodMonitorViewId = hwMap.appContext.getResources().getIdentifier(
                "tfodMonitorViewId", "id", hwMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_GOLD_MINERAL, LABEL_SILVER_MINERAL);
    }

    public void activateVision(){
        if(useVuforia){
            //TODO: finish vuforia trackables support.
        }
        if(useTfod && tfod != null){
            tfod.activate();
        }
    }

    public void deactivateVision(){
        if(useTfod && tfod != null){
            tfod.deactivate();
        }
    }

    public void shutdownVision(){
        if(useTfod && tfod != null){
            tfod.shutdown();
        }
    }
}
