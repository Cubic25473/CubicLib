package dev.cubiclib.ftc;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.util.*;
import static dev.cubiclib.recording.SessionRecorder.map;
final class DashboardServer extends NanoHTTPD {
    private final CubicLib lib;private final String token=UUID.randomUUID().toString();
    DashboardServer(CubicLib lib){super(CubicLib.PORT);this.lib=lib;}
    public Response serve(IHTTPSession request){
        String uri=request.getUri();
        try{
            if(request.getMethod()==Method.POST && uri.equals("/api/config")){
                if(!token.equals(request.getHeaders().get("x-cubic-token")))return json(Response.Status.FORBIDDEN,map("error","Missing setup token"));
                int length=Integer.parseInt(request.getHeaders().getOrDefault("content-length","0"));
                if(length<=0||length>16384)return json(Response.Status.BAD_REQUEST,map("error","Invalid configuration size"));
                Map<String,String> body=new HashMap<>();request.parseBody(body);
                RobotConfig config=lib.gson.fromJson(body.get("postData"),RobotConfig.class);
                if(config==null)throw new IllegalArgumentException("Missing configuration");lib.saveConfig(config);return json(Response.Status.OK,map("saved",true));
            }
            if(request.getMethod()!=Method.GET)return json(Response.Status.METHOD_NOT_ALLOWED,map("error","Method not allowed"));
            if(uri.equals("/api/status"))return json(Response.Status.OK,map("status",lib.status,"attached",lib.attached(),"token",token,"error",lib.startupError,"config",lib.config()));
            if(uri.equals("/api/live")){
                long after=Long.parseLong(request.getParms().getOrDefault("after","-1"));
                String requested=request.getParms().get("id");if(!Objects.equals(requested,lib.recorder.activeId()))after=-1;
                return json(Response.Status.OK,lib.recorder.live(after));
            }
            if(uri.equals("/api/sessions"))return json(Response.Status.OK,lib.recorder.sessions());
            if(uri.startsWith("/api/sessions/")){
                File file=lib.recorder.sessionFile(uri.substring("/api/sessions/".length()));
                if(!file.isFile())return json(Response.Status.NOT_FOUND,map("error","Session not found"));
                Response r=newFixedLengthResponse(Response.Status.OK,"application/x-ndjson",new FileInputStream(file),file.length());
                r.addHeader("Cache-Control","no-store");return r;
            }
            String asset=uri.equals("/")?"index.html":uri.substring(1);
            if(!Arrays.asList("index.html","app.js","analysis.js","bezier.js","style.css","field.json").contains(asset))return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","Not found");
            String mime=asset.endsWith(".js")?"text/javascript":asset.endsWith(".css")?"text/css":asset.endsWith(".json")?"application/json":"text/html";
            Response r=newChunkedResponse(Response.Status.OK,mime,lib.context.getAssets().open("cubiclib/"+asset));
            r.addHeader("X-Content-Type-Options","nosniff");r.addHeader("Cache-Control","no-cache");
            r.addHeader("Content-Security-Policy","default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'");return r;
        }catch(IllegalStateException e){return json(Response.Status.CONFLICT,map("error",e.getMessage()));}
        catch(Exception e){return json(Response.Status.BAD_REQUEST,map("error",String.valueOf(e.getMessage())));}
    }
    private Response json(Response.Status status,Object value){Response r=newFixedLengthResponse(status,"application/json",lib.gson.toJson(value));r.addHeader("Cache-Control","no-store");r.addHeader("X-Content-Type-Options","nosniff");return r;}
}
