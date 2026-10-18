package org.example;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;

/* The Todo CLI Application stores todos via a JSON Based file storage system locally.
 * Users can interact directly with a CLI interace to Add, Edit, Delete items in our stateful Todo database.
 */

public class TodoCliApplication {

    final static Path DEFAULT_PATH = Path.of("storage/save.json");
    static Path TARGET_PATH;
    static Map<Long, Todo> todoList = new HashMap<>();
    static Path userDefinedStoragePath;

    static inputState CURRENT_INPUT_STATE = inputState.DASHBOARD;

    public enum inputState {
        DASHBOARD, ADD, PRINT, EDIT, DELETE, EXIT;
    }

    public static void main(String[] args) {

        /*
         * This currently only looks at the first argument.
         * The first argument represents an alternative path for file-based database.
         */
        if (args.length > 0) {
            System.out.println(args[0]);
            TARGET_PATH = Path.of(args[0]);
        } else {
            TARGET_PATH = DEFAULT_PATH;
        }

        setupStorage(TARGET_PATH);
        loadDatabase();
        runCliApplication();
        storeDatabaseToFile();

        System.out.println("Terminating Todo CLI Application...");
    }

    private static void runCliApplication() {

        try (BufferedReader cliReader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true && CURRENT_INPUT_STATE != inputState.EXIT) {

                System.out.println();
                System.out.println("CLI command overview. NOTE: All commands are case-insenitive");
                System.out.println("============================================================");
                System.out.println();
                System.out.println("ADD     -- Add a new TODO to your list and store it.");
                System.out.println("EDIT    -- Edit an existing TODO in your list.");
                System.out.println("DELETE  -- Delete an existing TODO in your list.");
                System.out.println("PRINT   -- Print out the current TODO list.");
                System.out.println("EXIT    -- Terminate this current program.");
                System.out.println();
                System.out.print("> ");

                String selectedOption = cliReader.readLine();

                if (selectedOption == null) {
                    System.out.println("\nNull input. Closing input steam. Bye.");
                    break;
                }

                /*
                 * Match trimmed and uppercased version of input to options given.
                 * Selecting changes your current input state. EXIT state will exit the loop.
                 */
                switch (selectedOption.trim().toUpperCase()) {
                    case "ADD":
                        CURRENT_INPUT_STATE = inputState.ADD;
                        addTodo(cliReader);
                        break;

                    case "EDIT":
                        CURRENT_INPUT_STATE = inputState.EDIT;
                        editTodo(cliReader);
                        break;

                    case "DELETE":
                        CURRENT_INPUT_STATE = inputState.DELETE;
                        deleteTodo(cliReader);
                        break;

                    case "PRINT":
                        CURRENT_INPUT_STATE = inputState.PRINT;
                        printTodos();
                        break;

                    case "EXIT":
                        CURRENT_INPUT_STATE = inputState.EXIT;
                        break;
                    default:
                        System.out.println("Unrecognized input. Please review options.");
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Exception caught during CLI interaction. " + e.getMessage());
        }

    }

    /*
     * Creates the storage files at startup.
     * If the user passes a path for an alternative storage file, this method will
     * instantiate that
     * file here.
     */
    private static void setupStorage(Path alternativeStorage) {

        Path target = (alternativeStorage != null) ? alternativeStorage : DEFAULT_PATH;

        try {
            if (Files.notExists(target)) {
                Path parentDir = target.getParent();

                if (parentDir != null && Files.notExists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                Files.createFile(target);
            }
        } catch (IOException e) {
            System.err.println("Failed to create storage file: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private static void printTodos() {

        System.out.println();

        for (Long key : todoList.keySet()) {
            Todo cur = todoList.get(key);
            System.out.println(cur.toString());
        }
        CURRENT_INPUT_STATE = inputState.DASHBOARD;
    }

    private static void addTodo(BufferedReader reader) {

        try {

            System.out.println("Each Todo requires a Title and a Description. Please provide both when prompted.");
            System.out.println("When created, your Todo will be assigned a new unique ID.");
            System.out.println();

            Random rand = new Random();
            long min = 20_000L;
            long max = 100_000L;

            /*
             * Look for a new key until there is no conflicts.
             * If there is a conflict, the do... while loop will loop again
             * and generate a new key.
             * 
             * This ensures there are NO collisions and all IDs are unique.
             */
            Long newIdforTodo;

            do {
                newIdforTodo = rand.nextLong(min, max);
            } while (todoList.containsKey(newIdforTodo));

            // Long newIdforTodo = Long.valueOf(todoList.keySet().size() + 1);
            System.out.print("Enter title for Todo > ");
            String newTitleInput = reader.readLine();
            System.out.print("Enter description for Todo > ");
            String newDescInput = reader.readLine();

            Todo nextTodo = new Todo(newIdforTodo, newTitleInput, newDescInput);

            todoList.put(newIdforTodo, nextTodo);

            System.out.println();
            System.out.println("Todo successfully entered! " + nextTodo.toString());
            System.out.println();

        } catch (IOException ioe) {
            System.err.println("IO entry exception. " + ioe.getMessage());

        }

        CURRENT_INPUT_STATE = inputState.DASHBOARD;

        // Refresh database after adding new entry.
        storeDatabaseToFile();
    }

    private static void editTodo(BufferedReader reader) {

        try {
            printTodos();

            System.out.println();
            System.out.println(
                    "Please enters the unique numerical identifier of the entry you want to edit. (i.e. ID = 222)");
            System.out.print("> ");
            String inputForID = reader.readLine();

            Long parsedLong = Long.parseLong(inputForID);

            Todo replaceableTodo = todoList.get(parsedLong);

            System.out.print("New description > ");
            String newDescription = reader.readLine();
            todoList.put(parsedLong, new Todo(parsedLong, replaceableTodo.title(), newDescription));

            System.out.println();
            System.out.println("Todo successfully updated!");
            System.out.println();

        } catch (IOException ioe) {
            System.err.println("IO entry exception. " + ioe.getMessage());
        } catch (NumberFormatException nfe) {
            System.err.println("Improper format. Numerical values accepted only.");
        } catch (NullPointerException npe) {
            System.err.println("Unique identifier doesn't exist in database.");
        }

        CURRENT_INPUT_STATE = inputState.DASHBOARD;

        // Refresh database after edit.
        storeDatabaseToFile();
    }

    private static void deleteTodo(BufferedReader reader) {
        try {
            printTodos();

            System.out.println();
            System.out.println("Enter the unqiue ID for the Todo you want deleted.");
            System.out.print("> ");

            String idToDeleteInput = reader.readLine();
            Long idToDeleteLong = Long.parseLong(idToDeleteInput);

            Todo deletedTodo = todoList.get(idToDeleteLong);
            System.out.println("Todo to be deleted: " + deletedTodo.toString());

            todoList.remove(idToDeleteLong);

        } catch (IOException ioe) {
            System.err.println("IO entry exception. " + ioe.getMessage());
        } catch (NumberFormatException nfe) {
            System.err.println("Improper format. Numerical values accepted only.");
        } catch (NullPointerException npe) {
            System.err.println("Unique identifier doesn't exist in database.");
        }

        CURRENT_INPUT_STATE = inputState.DASHBOARD;

        // Refresh database after deletion.
        storeDatabaseToFile();
    }

    private static void loadDatabase() {

        try (FileReader reader = new FileReader(TARGET_PATH.toFile())) {

            Gson json = new GsonBuilder().create();
            Map<Long, Todo> tempList = json.fromJson(reader, new TypeToken<Map<Long, Todo>>() {
            }.getType());

            todoList = tempList;

        } catch (IOException e) {
            System.err.println("Input file reader failed: " + e.getMessage());
        } catch (JsonParseException jpe) { 
            System.err.println("Exception while parsing JSON. " + jpe.getMessage());
        }
    }

    private static void storeDatabaseToFile() {

        try (FileWriter writer = new FileWriter(TARGET_PATH.toFile(), StandardCharsets.UTF_8)) {
            Gson json = new GsonBuilder().setPrettyPrinting().create();
            json.toJson(todoList, writer);

        } catch (IOException e) {
            System.err.println("Output file writer failed: " + e.getMessage());
        } catch (JsonIOException jie) {
            System.err.println("Exception while encoding JSON. " + jie.getMessage());
        }

    }

}