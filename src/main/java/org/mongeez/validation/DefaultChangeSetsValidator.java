package org.mongeez.validation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.mongeez.commands.ChangeSet;
import org.mongeez.commands.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultChangeSetsValidator implements ChangeSetsValidator {

    private final static Logger logger = LoggerFactory.getLogger(DefaultChangeSetsValidator.class);

    @Override
    public void validate(List<ChangeSet> changesets) throws ValidationException {
        try {
        changeSetParamertsMissing(changesets);
        changeSetIdsNotUnique(changesets);
        validateChangeSetScripts(changesets);
        } catch (RuntimeException e) {
            logger.error("Error occured during change set validation. Please correct the issue and run again.");
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    private void changeSetIdsNotUnique(List<ChangeSet> changeSets) {
        Set<String> idSet = new HashSet<String>();
        for(ChangeSet changeSet: changeSets) {
            if (idSet.contains(changeSet.getChangeId())) {
                throw new ValidationException("ChangeSetId " + changeSet.getChangeId() + " is not unique.");
            }
            idSet.add(changeSet.getChangeId());
        }
    }
    
    private void changeSetParamertsMissing(List<ChangeSet> changeSets) {
        for(ChangeSet changeSet: changeSets) {
            if (StringUtils.isBlank(changeSet.getChangeId())) {
                throw new ValidationException("ChangeSetId is missing for changeset: " + changeSet.getChangeId() +" in file: "+ changeSet.getFile());
            }
            if (StringUtils.isBlank(changeSet.getCommandName())) {
                throw new ValidationException("CommandName is missing for changeset: " + changeSet.getChangeId() +" in file: "+ changeSet.getFile());
            }
        }
    }

    private void validateChangeSetScripts(List<ChangeSet> changeSets) {
        for (ChangeSet changeSet: changeSets) {
            for (Script script : changeSet.getCommands() ) {
                if (StringUtils.isBlank(script.getBody())) {
                    throw new ValidationException("Script missing for file: " + changeSet.getFile() + " and changeset: " + changeSet.getChangeId());
                }
                try {
                    Document.parse(script.getBody());
                } catch (Exception e) {
                    throw new ValidationException("Document parse error for changeset: " + changeSet.getChangeId() + 
                            " in file: " + changeSet.getFile(), e);
                }
            }
        }
    }

}
