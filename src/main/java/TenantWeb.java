import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.sun.net.httpserver.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import static com.mongodb.client.model.Filters.*;

public class TenantWeb {
    private static final String DB = "TenantClassDemo";
    private static final String SAMPLE = "class-example";
    private static final List<String> SECTIONS = List.of("tenants","properties","payments","receipts");
    private final MongoDatabase db;

    TenantWeb(MongoDatabase db) { this.db = db; }

    public static void main(String[] args) throws Exception {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) throw new IllegalStateException("Set MONGODB_URI in the service environment.");
        MongoClient client = MongoClients.create(uri);
        TenantWeb app = new TenantWeb(client.getDatabase(DB));
        app.seed();
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", Integer.parseInt(System.getenv().getOrDefault("PORT","8080"))), 0);
        server.createContext("/", app::route);
        server.setExecutor(Executors.newFixedThreadPool(8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {server.stop(1); client.close();}));
        server.start();
    }

    private void seed() {
        String[] names = {"Amina Demo","Brian Demo","Grace Demo","Ivan Demo","Joan Demo"};
        String[] areas = {"Kyanja","Ntinda","Bukoto","Kisaasi","Nakawa"};
        for (int i=0; i<names.length; i++) {
            int rent=250000 + i*50000, paid=i==4 ? rent-100000 : rent;
            String unit="A"+(i+1), receipt="DEMO-R0"+(i+1);
            example("properties", i, new Document("propertyName",areas[i]+" Court").append("area",areas[i]).append("unitNumber",unit).append("monthlyRent",rent));
            example("tenants", i, new Document("name",names[i]).append("unitNumber",unit).append("propertyName",areas[i]+" Court").append("monthlyRent",rent).append("status","Active"));
            Document payment=new Document("tenantName",names[i]).append("unitNumber",unit).append("amountPaid",paid).append("receiptNumber",receipt).append("period","2026-11");
            example("payments", i, new Document(payment).append("method",i%2==0?"Mobile money":"Cash"));
            example("receipts", i, new Document(payment).append("balance",rent-paid).append("status",rent==paid?"Fully paid":"Partial payment"));
        }
    }
    private void example(String collection,int index,Document row) {
        row.append("owner",SAMPLE);
        db.getCollection(collection).updateOne(eq("_id","demo-"+index),new Document("$setOnInsert",row),new UpdateOptions().upsert(true));
    }
    private void route(HttpExchange x) throws java.io.IOException {
        try {
            x.getResponseHeaders().set("Cache-Control","no-store");
            x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
            String path=x.getRequestURI().getPath(), method=x.getRequestMethod();
            if (method.equals("GET") && (path.equals("/") || path.equals("/app.js"))) {
                String asset=path.equals("/")?"/index.html":"/app.js";
                try(var stream=TenantWeb.class.getResourceAsStream(asset)) {
                    send(x,200,path.equals("/")?"text/html":"text/javascript",new String(Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8));
                }
                return;
            }
            String collection=path.startsWith("/api/")?path.substring(5):"";
            if (!SECTIONS.contains(collection)) {send(x,404,"text/plain","Unknown page");return;}
            String owner=owner(x);
            MongoCollection<Document> rows=db.getCollection(collection);
            if (method.equals("GET")) {
                List<String> result=new ArrayList<>();
                for(Document row:rows.find(in("owner",List.of(SAMPLE,owner))).limit(501)) {
                    row.append("editable",owner.equals(row.remove("owner")));
                    row.put("_id",row.get("_id").toString());
                    result.add(row.toJson());
                }
                send(x,200,"application/json","["+String.join(",",result)+"]");return;
            }
            if (!method.equals("POST") || !collection.equals("tenants") || !"1".equals(x.getRequestHeaders().getFirst("X-Tenant-Action"))) {
                send(x,403,"text/plain","Action unavailable");return;
            }
            byte[] raw=x.getRequestBody().readNBytes(4097);
            if(raw.length>4096)throw new IllegalArgumentException();
            Document body=Document.parse(new String(raw, StandardCharsets.UTF_8));
            String action=body.getString("action");
            var mine=and(eq("owner",owner),eq("_id",body.getString("id")));
            switch(action) {
                case "add" -> {
                    if(rows.countDocuments(eq("owner",owner))>=100)throw new IllegalArgumentException();
                    rows.insertOne(tenant(body).append("_id",new ObjectId().toHexString()).append("owner",owner));
                }
                case "edit" -> {if(rows.updateOne(mine,new Document("$set",tenant(body))).getMatchedCount()!=1)throw new IllegalArgumentException();}
                case "delete" -> {if(rows.deleteOne(mine).getDeletedCount()!=1)throw new IllegalArgumentException();}
                default -> throw new IllegalArgumentException();
            }
            send(x,200,"application/json","{}");
        } catch(com.mongodb.MongoException e) {
            send(x,503,"text/plain","Database unavailable. Check the hosted database connection.");
        } catch(RuntimeException e) {
            send(x,400,"text/plain","Check your tenant details or refresh the list.");
        } finally { x.close(); }
    }
    private static Document tenant(Document body) {
        Document tenant=new Document();
        for(String key:List.of("name","unitNumber","propertyName","status")) {
            String v=body.getString(key);
            if(v==null || v.isBlank() || v.length()>100)throw new IllegalArgumentException();
            tenant.put(key,v.trim());
        }
        if(!List.of("Active","Inactive").contains(tenant.getString("status")))throw new IllegalArgumentException();
        long rent=Long.parseLong(String.valueOf(body.get("monthlyRent")));
        if(rent<0 || rent>1000000000L)throw new IllegalArgumentException();
        return tenant.append("monthlyRent",rent);
    }
    private static String owner(HttpExchange x) {
        String cookies=x.getRequestHeaders().getFirst("Cookie");
        if(cookies!=null) for(String item:cookies.split(";")) {
            String s=item.trim();
            if(s.matches("tenant_demo=[a-f0-9-]{36}"))return s.substring("tenant_demo=".length());
        }
        String id=UUID.randomUUID().toString();
        x.getResponseHeaders().add("Set-Cookie","tenant_demo="+id+"; Path=/; HttpOnly; SameSite=Strict"+(System.getenv("RENDER")!=null?"; Secure":""));
        return id;
    }
    private static void send(HttpExchange x,int status,String type,String text) throws java.io.IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=UTF-8");
        x.sendResponseHeaders(status,bytes.length);
        x.getResponseBody().write(bytes);
    }
}
