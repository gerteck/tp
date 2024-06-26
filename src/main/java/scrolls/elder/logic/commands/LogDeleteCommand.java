package scrolls.elder.logic.commands;

import static java.util.Objects.requireNonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import scrolls.elder.commons.core.index.Index;
import scrolls.elder.commons.util.ToStringBuilder;
import scrolls.elder.logic.Messages;
import scrolls.elder.logic.commands.exceptions.CommandException;
import scrolls.elder.model.LogStore;
import scrolls.elder.model.Model;
import scrolls.elder.model.PersonStore;
import scrolls.elder.model.log.Log;
import scrolls.elder.model.person.Address;
import scrolls.elder.model.person.Email;
import scrolls.elder.model.person.Name;
import scrolls.elder.model.person.Person;
import scrolls.elder.model.person.PersonFactory;
import scrolls.elder.model.person.Phone;
import scrolls.elder.model.person.Role;
import scrolls.elder.model.tag.Tag;


/**
 * Deletes the log identified using its displayed index from the address book.
 */
public class LogDeleteCommand extends Command {

    public static final String COMMAND_WORD_LOG_DELETE = "logdelete";
    public static final String COMMAND_WORD_LOG_DEL = "logdel";
    public static final String COMMAND_WORD_LOG_RM = "logrm";
    public static final String COMMAND_WORD_LOG_REMOVE = "logremove";

    public static final String MESSAGE_USAGE = COMMAND_WORD_LOG_DELETE
            + ": Deletes the log identified by the index number used in the displayed log list.\n"
            + "Parameters: INDEX \n"
            + "Example: " + COMMAND_WORD_LOG_DELETE + " 1\n"
            + "Alternatively, you can also delete a log using the following commands as well.\n"
            + "Example: " + COMMAND_WORD_LOG_DEL + " 1\n"
            + "Example: " + COMMAND_WORD_LOG_RM + " 1\n"
            + "Example: " + COMMAND_WORD_LOG_REMOVE + " 1";

    public static final String MESSAGE_DELETE_LOG_SUCCESS = "Deleted Log: %1$s";
    public static final String MESSAGE_DELETE_LOG_ERROR = "Unable to delete log: ";
    private final Index targetIndex;

    /**
     * Creates a LogDeleteCommand to delete the log at the specified {@code targetIndex}.
     */
    public LogDeleteCommand(Index targetIndex) {
        this.targetIndex = targetIndex;
    }

    @Override
    public CommandResult execute(Model model) throws CommandException {
        requireNonNull(model);

        LogStore logStore = model.getMutableDatastore().getMutableLogStore();
        PersonStore personStore = model.getMutableDatastore().getMutablePersonStore();

        List<Log> lastShownList = logStore.getLogList();
        List<Person> lastShownPList = personStore.getPersonList();

        if (targetIndex.getZeroBased() >= lastShownList.size()) {
            throw new CommandException(MESSAGE_DELETE_LOG_ERROR + Messages.MESSAGE_INVALID_LOG_DISPLAYED_INDEX);
        }

        Log logToDelete = lastShownList.get(targetIndex.getZeroBased());
        int logIdToDelete = logToDelete.getLogId();

        int durationToDelete = logToDelete.getDuration();

        // Retrieve the volunteer and befriendee from the log to be deleted
        Person volunteer = personStore.getPersonFromID(logToDelete.getVolunteerId());
        Person befriendee = personStore.getPersonFromID(logToDelete.getBefriendeeId());

        // Get the latest log id of the volunteer and befriendee after deletion
        Optional<Integer> latestLogIdBefriendee =
                getLatestLogId(befriendee, logStore, logToDelete.getLogId());
        Optional<Integer> latestLogIdVolunteer =
                getLatestLogId(volunteer, logStore, logToDelete.getLogId());

        // Update the volunteer and befriendee with the new timeServed and latestLogId
        Person updatedVolunteer = createUpdatedPerson(volunteer, durationToDelete, latestLogIdVolunteer);
        Person updatedBefriendee = createUpdatedPerson(befriendee, durationToDelete, latestLogIdBefriendee);
        personStore.setPerson(volunteer, updatedVolunteer);
        personStore.setPerson(befriendee, updatedBefriendee);
        personStore.updateFilteredPersonList(Model.PREDICATE_SHOW_ALL_PERSONS);

        logStore.removeLog(logIdToDelete);
        logStore.updateFilteredLogList(LogStore.PREDICATE_SHOW_ALL_LOGS);
        logStore.updateFilteredLogListByPersonId(null);
        model.commitDatastore();
        return new CommandResult(String.format(MESSAGE_DELETE_LOG_SUCCESS, Messages.formatLog(logToDelete)));
    }

    /**
     * Create and return a {@code Person} with the details of {@code personToUpdate}
     * edited with the updated timeServed and latestLogId.
     */
    private Person createUpdatedPerson(Person personToUpdate, int duration, Optional<Integer> logId) {
        assert personToUpdate != null;

        Name name = personToUpdate.getName();
        Phone phone = personToUpdate.getPhone();
        Email email = personToUpdate.getEmail();
        Address address = personToUpdate.getAddress();
        Set<Tag> tags = personToUpdate.getTags();
        Role role = personToUpdate.getRole();
        Optional<Name> pairedWithName = personToUpdate.getPairedWithName();
        Optional<Integer> pairedWithId = personToUpdate.getPairedWithId();
        int updatedTimeServed = personToUpdate.getTimeServed() - duration;
        Optional<Integer> latestLogId = logId;

        return PersonFactory.withIdFromParams(personToUpdate.getPersonId(), name, phone, email, address, role,
                tags, pairedWithName, pairedWithId, updatedTimeServed, latestLogId);
    }

    /**
     * Returns the latest log id of the person after the log is deleted.
     */
    private Optional<Integer> getLatestLogId(Person person, LogStore logStore, Integer toDeleteId) {

        if (person.isLatestLogPresent()) {
            Log currentLatest = logStore.getLogById(person.getLatestLogId().get());

            if (toDeleteId != currentLatest.getLogId()) {
                return Optional.of(currentLatest.getLogId());
            }
            // the log deleted is displayed as the current latest log
            // the next latest log is then retrieved
            Log newCurrentLatest = logStore.getLogList().stream()
                    .filter(log -> log.getBefriendeeId() == person.getPersonId()
                            || log.getVolunteerId() == person.getPersonId())
                    .filter(log -> log.getLogId() != toDeleteId)
                    .max(Comparator.comparing(Log::getStartDate)).orElse(null);

            // newCurrentLatest is returned as latest log if present
            if (newCurrentLatest != null) {
                return Optional.of(newCurrentLatest.getLogId());
            } else {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }

        //instanceof handles nulls
        if (!(other instanceof LogDeleteCommand)) {
            return false;
        }

        LogDeleteCommand otherLogDeleteCommand = (LogDeleteCommand) other;
        return targetIndex.equals(otherLogDeleteCommand.targetIndex);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .add("targetIndex", targetIndex)
                .toString();
    }
}
