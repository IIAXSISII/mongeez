package org.mongeez;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;

public class DBCommandsMongeezTest {
    private String dbName = "mongeez-test";
    String mongoHostname = "127.0.0.1";
    int mongodbPort = 27017;
    String mongoUsername = "mongeezuser";
    String mongoPassword = "test123";

    private MongoClient mongoClient;
    private MongoAuth mongoAuth;
    private MongoDatabase mongoDb;

	@Before
    public void setUp() throws Exception {

        MongoCredential credential = MongoCredential.createCredential(mongoUsername, dbName, mongoPassword.toCharArray());
        mongoAuth = new MongoAuth(mongoUsername, mongoPassword, dbName);
        MongoClientOptions options = MongoClientOptions.builder().build();
        List<ServerAddress> addresses = new ArrayList<ServerAddress>();
        ServerAddress address = new ServerAddress(mongoHostname, mongodbPort);
        addresses.add(address);
        mongoClient = new MongoClient(addresses, credential, options);
        mongoDb = mongoClient.getDatabase(dbName);
        mongoDb.drop();
    }

    private Mongeez create(String path) {
        Mongeez mongeez = new Mongeez();
        mongeez.setFile(new ClassPathResource(path));
        mongeez.setMongo(mongoClient);
        mongeez.setDbName(dbName);
        mongeez.setAuth(mongoAuth);
        return mongeez;
    }

    @Test
    public void testCollectionCreation() throws Exception {
        Mongeez mongeez = create("collection_create.xml");

        mongeez.process();

        List<String> expectedCollectionNames = Arrays.asList("mongeez","playstation", "xbox", "wii", "playstation4games");
        MongoIterable<String> actualCollectionNames = mongoDb.listCollectionNames();

        for (String collectionName : actualCollectionNames) {
            assertTrue(expectedCollectionNames.contains(collectionName));
        }
    }

    @Test
    public void testIndexCreation() throws Exception {
        Mongeez mongeez = create("collection_create.xml");
        mongeez.process();

        mongeez = create("create_index.xml");
        mongeez.process();

        String collectionName = "playstation";
        List<String> expectedIndexes = Arrays.asList("_id_","psnId_1", "userId_1", "createdDate_1", "insertTimestamp_1", "sourceName_1");
        verifyIndexes(expectedIndexes, collectionName);

        collectionName = "wii";
        expectedIndexes = Arrays.asList("_id_","wiiId_1");
        verifyIndexes(expectedIndexes, collectionName);

        collectionName = "playstation4games";
        expectedIndexes = Arrays.asList("_id_","gameId_1");
        verifyIndexes(expectedIndexes, collectionName);

        collectionName = "xbox";
        expectedIndexes = Arrays.asList("_id_","sourceName_1", "xboxId_1");
        verifyIndexes(expectedIndexes, collectionName);

    }

    private void verifyIndexes(List<String> expectedIndexes, String collectionName) {
        ListIndexesIterable<Document> indexes = mongoDb.getCollection(collectionName).listIndexes();
        for (Document index : indexes) {
            assertTrue(expectedIndexes.contains(index.get("name")));
        }
    }

}
