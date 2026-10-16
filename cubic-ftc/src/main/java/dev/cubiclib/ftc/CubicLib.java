package dev.cubiclib.ftc;
import android.content.Context;
import com.google.gson.Gson;
import dev.cubiclib.recording.SessionRecorder;
import org.firstinspires.ftc.ftccommon.external.OnCreate;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** One dashboard per Robot Controller process, including while no OpMode is selected. */
public final class CubicLib {
    public static final int PORT=8087;
    private static CubicLib instance;
    final Gson gson=new Gson();final Context context;final File configFile;
    public final SessionRecorder recorder;
    private RobotConfig config;private boolean attached;
    final DashboardServer server;
    public volatile String status="Ready",startupError="";
    @OnCreate public static void onCreate(Context context){
        try{install(context);}catch(Exception e){android.util.Log.e("CubicLib","Dashboard startup failed",e);}
    }
    public static synchronized CubicLib install(Context context)throws IOException{
        if(instance==null)instance=new CubicLib(context.getApplicationContext());return instance;
    }
    private CubicLib(Context context)throws IOException{
        this.context=context;File root=new File(context.getFilesDir(),"cubiclib");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create CubicLib storage");
        configFile=new File(root,"robot.json");config=new RobotConfig();
        if(configFile.isFile())try(Reader reader=new InputStreamReader(new FileInputStream(configFile),StandardCharsets.UTF_8)){
            config=gson.fromJson(reader,RobotConfig.class);config.validate();
        }catch(Exception e){config=new RobotConfig();startupError="Saved configuration invalid; repair setup before Init: "+e.getMessage();}
        recorder=new SessionRecorder(new File(root,"sessions"));server=new DashboardServer(this);server.start(3000,true);
    }
    public synchronized RobotConfig config(){return gson.fromJson(gson.toJson(config),RobotConfig.class);}
    public synchronized RobotConfig attach(){
        if(attached)throw new IllegalStateException("An OpMode is already attached");
        if(!startupError.isEmpty())throw new IllegalStateException(startupError);
        config.validate();attached=true;status="Initialized";return config();
    }
    public synchronized void detach(){attached=false;status="Ready";}
    public synchronized boolean attached(){return attached;}
    public synchronized void saveConfig(RobotConfig next)throws IOException{
        if(attached)throw new IllegalStateException("Stop the OpMode before changing configuration");next.validate();
        File temp=new File(configFile.getParentFile(),"robot.json.tmp");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(temp),StandardCharsets.UTF_8)){gson.toJson(next,out);}
        if(!temp.renameTo(configFile))throw new IOException("Could not save configuration");
        config=gson.fromJson(gson.toJson(next),RobotConfig.class);startupError="";
    }
}
