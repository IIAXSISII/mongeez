/*
 * Copyright 2011 SecondMarket Labs, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at  http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mongeez.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mongeez.MongoAuth;
import org.mongeez.commands.ChangeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientOptions.Builder;
import com.mongodb.MongoCredential;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.UpdateResult;

public class MongeezDao {

    private final Logger logger = LoggerFactory.getLogger(MongeezDao.class);

    private MongoDatabase mongoDb;
    private List<ChangeSetAttribute> changeSetAttributes;

    private final static String TYPE_EXISTS_FALSE_FILTER_QUERY = "{ \"type\" : { \"$exists\" : false}}";
    private final static String TYPE_CONFIG_FILTER_QUERY = "{ \"type\" : \""+ RecordType.configuration.name() +"\"}";

    public MongeezDao(MongoClient mongoClient, String databaseName) {
        this(mongoClient, databaseName, null);
    }

	public MongeezDao(MongoClient mongoClient, String databaseName, MongoAuth auth) {

        String authDbName = StringUtils.EMPTY;
        if (auth != null) {
            if (StringUtils.isBlank(auth.getAuthDb())) {
                authDbName = databaseName;
            } else {
                authDbName = auth.getAuthDb();
            }
        } else {
        	throw new IllegalStateException("Missing Mongodb authentication. Set username, password and authdb.");
        }

        final MongoCredential credential = MongoCredential.createCredential(auth.getUsername(), authDbName, auth.getPassword().toCharArray());
        Builder mongoClientOptionsBuilder = MongoClientOptions.builder();
        MongoClientOptions mongoClientOptions = mongoClientOptionsBuilder.build();
        final MongoClient client = new MongoClient(mongoClient.getServerAddressList(), credential, mongoClientOptions);
        mongoDb = client.getDatabase(databaseName);
        configure();
    }

    private void configure() {
        addTypeToUntypedRecords();
        loadConfigurationRecord();
        dropObsoleteChangeSetExecutionIndices();
        ensureChangeSetExecutionIndex();
    }

    private void addTypeToUntypedRecords() {
        Document filterQuery = Document.parse(TYPE_EXISTS_FALSE_FILTER_QUERY);
        Document updateQuery = new Document("$set", new Document("type", RecordType.changeSetExecution.name()));

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        try {
            UpdateResult updateResult = getMongeezCollection().updateMany(filterQuery, updateQuery, updateOptions);
            if (updateResult.wasAcknowledged()) {
                logger.info("Query for addTypeToUntypedRecords was acknowledged. Number of matched records: " + updateResult.getMatchedCount() +
                        "    Number of updated records: " + updateResult.getModifiedCount());
            } else {
                logger.info("Query for addTypeToUntypedRecords was not acknowledged");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void loadConfigurationRecord() {
        Document filterQuery = Document.parse(TYPE_CONFIG_FILTER_QUERY);
        FindIterable<Document> findResult = getMongeezCollection().find(filterQuery);
        Document configRecord = findResult.first();

        if (configRecord == null) {
            if (getMongeezCollection().count() > 0L) {
                // We have pre-existing records, so don't assume that they support the latest features
                configRecord =
                        new Document()
                                .append("type", RecordType.configuration.name())
                                .append("supportResourcePath", false);
            } else {
                configRecord =
                        new Document()
                                .append("type", RecordType.configuration.name())
                                .append("supportResourcePath", true);
            }
            getMongeezCollection().insertOne(configRecord, new InsertOneOptions());
        }

        Object supportResourcePath = configRecord.get("supportResourcePath");
        logger.info("Inserted configuration record as: " + supportResourcePath);

        changeSetAttributes = new ArrayList<ChangeSetAttribute>();
        changeSetAttributes.add(ChangeSetAttribute.file);
        changeSetAttributes.add(ChangeSetAttribute.changeId);
        changeSetAttributes.add(ChangeSetAttribute.author);
        if (Boolean.TRUE.equals(supportResourcePath)) {
            changeSetAttributes.add(ChangeSetAttribute.resourcePath);
        }
    }

    /**
     * Removes indices that were generated by versions before 0.9.3, since they're not supported by MongoDB 2.4+
     */
    private void dropObsoleteChangeSetExecutionIndices() {
        String indexName = "type_changeSetExecution_file_1_changeId_1_author_1_resourcePath_1";
        MongoCollection<Document> collections = getMongeezCollection();
        for (Document doc : collections.listIndexes()) {
            if (StringUtils.equals(doc.get("name").toString(), indexName)) {
                collections.dropIndex(indexName);
            }
        }
    }

    private void ensureChangeSetExecutionIndex() {
        Document keys = new Document();
        keys.append("type", 1);
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            keys.append(attribute.name(), 1);
        }
        getMongeezCollection().createIndex(keys);
    }

    public boolean wasExecuted(ChangeSet changeSet) {
        Document query = new Document();
        query.append("type", RecordType.changeSetExecution.name());
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            query.append(attribute.name(), attribute.getAttributeValue(changeSet));
        }
        return getMongeezCollection().count(query) > 0;
    }

    private MongoCollection<Document> getMongeezCollection() {
        return mongoDb.getCollection("mongeez");
    }

    public Document runScript(Bson command) {
        logger.info("Starting execution of the script:  " + command.toString());
        Document result = mongoDb.runCommand(command);
        return result;
    }

    public void logChangeSet(ChangeSet changeSet) {
        Document object = new Document();
        object.append("type", RecordType.changeSetExecution.name());
        for (ChangeSetAttribute attribute : changeSetAttributes) {
            object.append(attribute.name(), attribute.getAttributeValue(changeSet));
        }
        object.append("date", DateFormatUtils.ISO_DATETIME_TIME_ZONE_FORMAT.format(System.currentTimeMillis()));
        InsertOneOptions options = new InsertOneOptions();
        options.bypassDocumentValidation(true);
        getMongeezCollection().insertOne(object, options);
    }
}
