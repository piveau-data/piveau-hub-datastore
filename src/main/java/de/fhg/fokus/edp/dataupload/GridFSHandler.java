package de.fhg.fokus.edp.dataupload;



public class GridFSHandler {
    /*private static String dbConnection;

    public GridFS getGridFS() {
        return gridFS;
    }

    private GridFS gridFS;

    public GridFSHandler(String dbConnection) {
        this.dbConnection = dbConnection;
    }

    public Future init(String dbName){
        Future<Void> future = Future.future();

       // MongoClient mongoClient = new MongoClient(new MongoClientURI(dbConnection));
        MongoClient mongoClient = new MongoClient("mongodb://localhost:27017");
        MongoDatabase fileDB = mongoClient.getDatabase("FileDB");
        DB database = mongoClient.getDB("FileDB");

        gridFS = new GridFS(database);

        if(ObjectUtils.allNotNull(gridFS)){
            future.complete();
        }else {
            future.fail("No DB-Connection.");
        }

        return future;

    }*/
}
