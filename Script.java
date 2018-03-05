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
package org.mongeez.commands;

import org.mongeez.dao.MongeezDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBObject;

/**
 * @author oleksii
 * @since 5/3/11
 */
public class Script {

    private final Logger logger = LoggerFactory.getLogger(MongeezDao.class);
	private String body;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public CommandResult run(MongeezDao dao) {
        final DBObject command = new BasicDBObject();
        String function = "function() { " + body + "}";
        logger.info("Executing the function as eval:  " + function);
        command.put("eval", function);
        return dao.runScript(command);
    }
}


