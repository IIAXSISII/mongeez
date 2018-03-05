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

package org.mongeez;

import java.util.List;

import org.mongeez.commands.ChangeSet;
import org.mongeez.commands.Script;
import org.mongeez.dao.MongeezDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.CommandResult;
import com.mongodb.MongoClient;


public class ChangeSetExecutor {
    private final Logger logger = LoggerFactory.getLogger(ChangeSetExecutor.class);

    private MongeezDao dao = null;
    private String context = null;

    public ChangeSetExecutor(MongoClient mongoClient, String dbName, String context) {
        this(mongoClient, dbName, context, null);
    }

    public ChangeSetExecutor(MongoClient mongoClient, String dbName, String context, MongoAuth auth) {
        dao = new MongeezDao(mongoClient, dbName, auth);
        this.context = context;
    }

    public void execute(List<ChangeSet> changeSets) {
        for (ChangeSet changeSet : changeSets) {
            if (changeSet.canBeAppliedInContext(context)) {
                if (changeSet.isRunAlways() || !dao.wasExecuted(changeSet)) {
                    execute(changeSet);
                    logger.info("ChangeSet " + changeSet.getChangeId() + " has been executed");
                } else {
                    logger.info("ChangeSet already executed: " + changeSet.getChangeId());
                }
            }
            else {
                logger.info("Not executing Changeset {} it cannot run in the context {}", changeSet.getChangeId(), context);
            }
        }
    }

    private void execute(ChangeSet changeSet) {
        try {
            for (Script command : changeSet.getCommands()) {
                CommandResult commandResult = command.run(dao);
                commandResult.throwOnError();
                logger.info("ChangeSet " + changeSet.getChangeId() + " successfuly executed.");
            }
        } catch (RuntimeException e) {
            if (changeSet.isFailOnError()) {
                logger.error("ChangeSet " + changeSet.getChangeId() + " has failed, but failOnError is set to false", e);
                throw e;
            } else {
                logger.warn("ChangeSet " + changeSet.getChangeId() + " has failed, but failOnError is set to false", e);
            }
        }
        dao.logChangeSet(changeSet);
    }
}
