package de.fhg.fokus.edp.dataupload;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.MongoClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DBHandler {

    private MongoClient mongoClient;
 //   private GridFS gridFS;


    public DBHandler(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
        //this.gridFS = gridFS;
    }


    public void prepareEntry(JsonArray jsonArray, Handler<String> handler) {
        Future<String> future = Future.future();
        List<BulkOperation> jsonList = new ArrayList<>();
        for (Object object : jsonArray) {
            BulkOperation bulkOperation = BulkOperation.createInsert((JsonObject) object);

            jsonList.add(bulkOperation);
        }


            mongoClient.bulkWrite("fileuploaderdb",jsonList, res -> {
                if (res.succeeded()) {
                    handler.handle("success");
                } else {
                    future.fail(res.cause());
                    System.out.println(res.cause());
                    handler.handle(res.cause().toString());
                }
            });


    }

    public void createEntry(byte[] bytes, String filename, String uuid, String token, Handler<String> handler) {
        Future<String> future = Future.future();

        JsonObject queryDocument = new JsonObject()
                .put("token",token)
                .put("id",uuid);
        mongoClient.find("fileuploaderdb",queryDocument, res ->{
            if(res.result().isEmpty()){
                handler.handle("token");
                future.fail("token");
                return;
            }else{
                deleteEntryByToken(token, res2 ->{
                    if(!ObjectUtils.allNotNull(res2)) {
                        handler.handle(res2);
                    }
                });

                JsonObject document = null;
                document = new JsonObject()
                        .put("id", uuid)
                        .put("binaryData", bytes)
                        .put("token", token)
                        .put("fileName",filename);

                mongoClient.insert("fileuploaderdb", document, res3 -> {
                    if (res.succeeded()) {
                        future.complete("success");
                        handler.handle("success");
                    } else {
                        future.fail(res3.cause());
                        handler.handle(res3.cause().toString());
                    }
                });
            }
        });



    }


    private void deleteEntryByToken(String token, Handler<String> handler){
        Future<String> future = Future.future();
        JsonObject document = new JsonObject()
                .put("token",token);
        mongoClient.removeDocuments("fileuploaderdb",document, res ->{
            if (res.succeeded()) {
                res.result();
                future.complete(res.result().toString());
                handler.handle("Deleting complete.");
            } else {
                future.fail(res.cause());
                handler.handle(null);
            }
        });
    }


    public void deleteEntryByFileID(String fileID, Handler<String> handler){
        Future<String> future = Future.future();
        JsonObject document = new JsonObject()
                .put("id",fileID);
        mongoClient.removeDocuments("fileuploaderdb",document, res ->{
            if (res.succeeded()) {
                res.result();
                future.complete(res.result().toString());
                handler.handle("Deleting complete.");
            } else {
                future.fail(res.cause());
                handler.handle(null);
            }
        });

    }


    public void showAllForOwner(String ownerID, Handler<List> handler){
        JsonObject queryDocument = new JsonObject()
                .put("owner",ownerID);
        mongoClient.find("fileuploaderdb",queryDocument, res ->{

            if (res.succeeded()) {
                List<JsonObject> result = res.result();
                List<String> returnList= new ArrayList<>();
                for (JsonObject entries : result) {
                    entries.remove("_id");
                    entries.remove("BinaryData");
                }

                handler.handle(res.result());
            } else {
                handler.handle(null);
            }
        });

    }

    public void getFile(String fileID, Handler<File> aHandler){
        JsonObject queryDocument = new JsonObject()
                .put("id",fileID);
        AtomicReference<String> tmpFileName= new AtomicReference<>("");
        mongoClient.find("fileuploaderdb",queryDocument, res ->{

            if (res.succeeded() && res.result().size()>0) {
                JsonObject returnDocument = res.result().get(0);
                tmpFileName.set("/tmp/" + returnDocument.getString("fileName"));
                try {
                    FileUtils.writeByteArrayToFile(new File(String.valueOf(tmpFileName)), returnDocument.getBinary("binaryData"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                returnDocument.getBinary("binaryData");
                aHandler.handle(new File(String.valueOf(tmpFileName)));
            } else {
                aHandler.handle(null);
            }
        });

    }


}
