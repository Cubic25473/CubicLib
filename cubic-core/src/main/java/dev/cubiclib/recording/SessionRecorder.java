package dev.cubiclib.recording;
import com.google.gson.Gson;
import dev.cubiclib.odometry.Pose2;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Bounded, nonblocking producer. Disk work runs on a dedicated thread. JSONL survives interruption. */
public final class SessionRecorder implements AutoCloseable {
    private final Gson gson=new Gson();
    private final File directory;
    private volatile String error="";
    private Session current;
    public SessionRecorder(File directory){this.directory=directory;if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalArgumentException("Cannot create log directory");}
    public synchronized void start(String opMode,Object config){
        if(current!=null && current.thread.isAlive())throw new IllegalStateException("Previous recording is active or finishing");
        File[] old=directory.listFiles((dir,name)->name.endsWith(".jsonl"));
        if(old!=null){Arrays.sort(old,Comparator.comparingLong(File::lastModified));for(int i=0;i<old.length-19;i++)if(!old[i].delete())throw new IllegalStateException("Cannot prune old log");}
        error="";current=new Session(opMode,config);current.thread.start();
    }
    public synchronized boolean recording(){return current!=null&&!current.closing;}
    public synchronized void sample(Pose2 pose,boolean valid,int generation,double battery,double loopMs){
        Session s=current;if(s==null||s.closing)return;
        long now=System.nanoTime();if(now-s.lastSample<50_000_000L)return;s.lastSample=now;
        if(s.samples.size()>=18000){error="15-minute session limit reached";stop();return;}
        Sample point=new Sample(s.sequence++, (now-s.started)/1e9,pose,generation,valid,battery,loopMs);
        s.samples.add(point);s.offer(point);
    }
    public synchronized void event(String name){
        Session s=current;if(s==null||s.closing)return;
        if(name==null||name.length()>160)throw new IllegalArgumentException("Event name must be <=160 characters");
        if(s.events.size()>=5000){s.dropped++;return;}
        Map<String,Object> event=map("type","event","t",(System.nanoTime()-s.started)/1e9,"name",name);
        s.events.add(event);s.offer(event);
    }
    public synchronized void stop(){if(current!=null)current.closing=true;}
    public synchronized Map<String,Object> live(long after){
        Session s=current;
        if(s==null)return map("recording",false,"error",error,"samples",Collections.emptyList(),"events",Collections.emptyList());
        List<Sample> samples=new ArrayList<>();for(Sample p:s.samples)if(p.sequence>after)samples.add(p);
        return map("id",s.id,"recording",!s.closing,"finishing",s.closing&&s.thread.isAlive(),"error",error,"dropped",s.dropped,
            "meta",s.meta,"samples",samples,"events",new ArrayList<>(s.events),"elapsed",(System.nanoTime()-s.started)/1e9);
    }
    public synchronized String activeId(){return current==null?null:current.id;}
    public List<Map<String,Object>> sessions(){
        List<Map<String,Object>> out=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".jsonl"));
        if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File f:files)out.add(map("id",f.getName().replace(".jsonl",""),"bytes",f.length()));}return out;
    }
    public File sessionFile(String id){if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid session ID");return new File(directory,id+".jsonl");}
    public void close(){stop();}
    public synchronized Thread writerThread(){return current==null?null:current.thread;}
    public static Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private final class Session {
        final String id=UUID.randomUUID().toString();final long started=System.nanoTime();long lastSample=started-50_000_000L,sequence;
        final ArrayBlockingQueue<Object> queue=new ArrayBlockingQueue<>(2048);
        final List<Sample> samples=new ArrayList<>();final List<Map<String,Object>> events=new ArrayList<>();
        final Map<String,Object> meta;final Thread thread;
        volatile boolean closing;volatile long dropped;
        Session(String name,Object config){meta=map("type","meta","schema",1,"id",id,"opMode",name,"startedAt",System.currentTimeMillis(),"config",config);
            thread=new Thread(this::write,"CubicLib recorder");thread.setDaemon(true);}
        void offer(Object item){if(!queue.offer(item))dropped++;}
        void write(){
            try(BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sessionFile(id)),StandardCharsets.UTF_8))){
                line(writer,meta);long flushAt=System.nanoTime();
                while(!closing||!queue.isEmpty()){
                    Object item=queue.poll(100,TimeUnit.MILLISECONDS);if(item!=null)line(writer,item);
                    if(System.nanoTime()-flushAt>1_000_000_000L){writer.flush();flushAt=System.nanoTime();}
                }
                line(writer,map("type","end","dropped",dropped,"error",error));
            }catch(Exception e){error="Recording failed: "+e.getClass().getSimpleName()+": "+e.getMessage();closing=true;}
        }
        void line(BufferedWriter w,Object obj)throws IOException{w.write(gson.toJson(obj));w.newLine();}
    }
}
