package de.fhg.fokus.edp.dataupload;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;


public class MainVerticle extends AbstractVerticle {

    private Logger log = LoggerFactory.getLogger(getClass());
    private MongoClient mongoClient;
    private DBHandler dbHandler;
    private JsonObject config;

    public static void main(String[] args) {
        Launcher.executeCommand("run", MainVerticle.class.getName());
    }

    @Override
    public void start(Future<Void> startFuture) {
        Future<Void> steps = loadConfig()
                .compose(handler -> setupMongoDBConnection())
                .compose(handler -> startServer());


        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                log.info("Fileuploader successfully launched");
            } else {
                log.error("Failed to launch Fileuploader: " + handler.cause());
                System.out.println("Error: " + handler.cause());
            }
        });

    }

    private Future<Void> loadConfig() {
        Future<Void> future = Future.future();


        ConfigStoreOptions sysPropsStore = new ConfigStoreOptions().setType("env");

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(sysPropsStore);

        ConfigRetriever.create(vertx,options).getConfig(handler -> {
            if (handler.succeeded()) {
                config = handler.result();
                future.complete();
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> setupMongoDBConnection() {

        Future<Void> future = Future.future();
        JsonObject dbconfig = new JsonObject()
                .put("connection_string", config.getString("MONGO_DB_URI"))
                .put("db_name", config.getString("MONGO_DB"));

        mongoClient = MongoClient.createShared(vertx, dbconfig);
        if (ObjectUtils.allNotNull(mongoClient)) {
            future.complete();
        } else {
            future.fail("No DB-Connection.");
        }

        dbHandler = new DBHandler(mongoClient);


        return future;

    }

    private Future<Void> startServer() {
        Future<Void> future = Future.future();

        OpenAPI3RouterFactory.create(vertx, "webroot/openapi.yml", ar -> {
            if (ar.succeeded()) {
                // Spec loaded with success
                OpenAPI3RouterFactory routerFactory = ar.result();
                routerFactory.addSecurityHandler("ApiKeyAuth", this::checkApiKey);
                routerFactory.addHandlerByOperationId("singleFileUpload", this::handleSingleFileUpload);
                routerFactory.addHandlerByOperationId("prepareUpload", this::handlePrepareUpload);
                routerFactory.addHandlerByOperationId("deleteEntry", this::handleDeleteEntry);
                routerFactory.addHandlerByOperationId("getFile", this::handleGetFile);
                Router router = routerFactory.getRouter();
                router.route("/*").handler(StaticHandler.create());
                HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(config.getInteger("HTTP_PORT")));
                server.requestHandler(router::accept).listen();
                future.complete();
            } else {
                // Something went wrong during router factory initialization
                future.fail(ar.cause());
            }
        });

        return future;

    }

    public void checkApiKey(RoutingContext context) {

        String apiKey = config.getString("API_KEY");

        final String authorization = context.request().headers().get(HttpHeaders.AUTHORIZATION);

        if(apiKey.isEmpty()) {
            JsonObject response = new JsonObject();
            context.response().putHeader("Content-Type", "application/json");
            context.response().setStatusCode(500);
            response.put("success", false);
            response.put("message", "Api-Key is not specified");
        } else if(authorization == null) {
            JsonObject response = new JsonObject();
            context.response().putHeader("Content-Type", "application/json");
            context.response().setStatusCode(401);
            response.put("success", false);
            response.put("message", "Header field Authorization is missing");
            context.response().end(response.toString());
        } else if (!authorization.equals(apiKey)) {
            JsonObject response = new JsonObject();
            context.response().putHeader("Content-Type", "application/json");
            context.response().setStatusCode(401);
            response.put("success", false);
            response.put("message", "Incorrect Api-Key");
            context.response().end(response.toString());
        } else {
            context.next();
        }
    }

    private void handlePrepareUpload(RoutingContext context) {
        dbHandler.prepareEntry( context.getBodyAsJsonArray(), res -> {
            if (res.equals("success")) {
                context.response().setStatusCode(200);
                context.response().end();
            } else {
                context.response().setStatusCode(500);
                context.response().end("Could not create entry: "+res);
            }
        });



    }

    private void handleSingleFileUpload(RoutingContext context) {

        if (context.fileUploads().size() != 1) {
            HttpServerResponse response = context.response();
            response.setStatusCode(500);
            response.end("Only one file allowed in upload");
            return;
        }

        String fileName = null;
        byte[] bytes = null;
        for (FileUpload f : context.fileUploads()) {
            File uploadFile = new File(f.uploadedFileName());
            fileName = f.fileName();
            try {
                bytes = Files.readAllBytes(uploadFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        dbHandler.createEntry(bytes,fileName,context.pathParam("id"), context.queryParam("token").get(0), res -> {
            if (res.equals("success")) {
                context.response().putHeader("Content-Type", "application/json");
                context.response().setStatusCode(200);
                context.response().end();

            } else if(res.equals("token")){
                context.response().setStatusCode(500);
                context.response().end("Could not create entry: Mismatch between token und id.");
            }else{
                context.response().setStatusCode(500);
                context.response().end("Could not create entry: "+res);
            }
        });


    }

    private void handleDeleteEntry(RoutingContext context) {
        dbHandler.deleteEntryByFileID(context.pathParam("id"),res ->{
            if(ObjectUtils.allNotNull(res)){
                context.response().setStatusCode(200);
                context.response().end("Deleting complete");
            }else{
                context.response().setStatusCode(500);
                context.response().end("Could not delete entry.");
            }
        });

    }

    private void handleGetFile(RoutingContext context) {
        HttpServerResponse response = context.response();

        dbHandler.getFile(context.request().getParam("id"), res -> {
            if (ObjectUtils.allNotNull(res)) {
                response.sendFile(res.getAbsolutePath());
                response.setStatusCode(200);
                res.delete();
            } else {
                response.setStatusCode(500);
                response.end("File not found with fileID: " +context.request().getParam("id"));
            }
        });

    }

}
