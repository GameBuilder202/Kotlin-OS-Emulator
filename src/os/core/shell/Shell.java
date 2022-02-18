package os.core.shell;

import os.core.filesystem.File;
import os.core.filesystem.Folder;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shell
{
	private final InputStream in;
	private final Scanner scanner;
	private final PrintStream printStream;

	private String currentPath = "~";
	private String currentFullPath;
	private Folder currentFolder;

	private Folder rootFolder;
	private String username;

	private JSONObject rootObject;

	private static int idCounter = 0;
	private final int id;

	private static final String _default =
			"""
			{
			  "isInstalled": false,

			  "root": [
				{
				  "name": "Home",
				  "folders": [
					{
					  "name": "Desktop",
					  "folders": [],
					  "files": []
					}
				  ],
				  "files": []
				},
				{
				  "name": "libs",
				  "folders": [],
				  "files": []
				}
			  ],

			  "username": ""
			}
			"""
			.replace("\t", "")
			.replace("\n", "")
			.replace(" " , "");

	private static final HashMap<String, String> helpMap;

	public static final String ERR = "\033[31m";
	public static final String RESET = "\033[0m";

	static
	{
		helpMap = new HashMap<>();
		helpMap.put("help",
				"""
				help - Shows help for all commands
				help <command> - Shows detailed help for a particular command
				""");
		helpMap.put("shutdown",
				"""
				Shutdown the OS
				Additionally, runs the garbage collector to clean up resources, and updates os info json file
				""");
		helpMap.put("reboot",
				"""
				Reboots the OS
				Additionally, runs the garbage collector to clean up resources, and updates os info json file
				""");
		helpMap.put("mkdir",
				"""
				Makes a new directory relative to the current directory
				Subdirectories are separated with /
				Spaces separate different folders to be created
				""");
		helpMap.put("mk",
				"""
				Makes a new file relative to the current directory
				Subdirectories are separated with /
				Spaces separate different files to be created
				""");
		helpMap.put("rmdir",
				"""
				Deletes a folder relative to the current directory
				Subdirectories are separated with /
				Spaces separate different folders to be deleted
				""");
		helpMap.put("rm",
				"""
				Deletes a file relative to the current directory
				Subdirectories are separated with /
				Spaces separate different files to be deleted
				""");
		helpMap.put("cd",
				"""
				Change directory into a subfolder
				To change directory relative to Home folder, use ~ at the start
				""");
		helpMap.put("ls",
				"""
				List all files and folders under the current directory
				Use flag -tree to list in a tree format with all sub-files and sub-folders
				""");
		helpMap.put("clear",
				"""
				Clears the screen
				""");
		helpMap.put("vim",
				"""
				Opens vim command line text editor on the specified filename
				Currently does not open files in a subdirectory
				
				Vim commands:
				:w# text - Write text to line #
				:i# text - Insert text at line #
				:r#      - Remove line #
				:c#      - Clear line #
				:s       - Save file
				:q       - Quit vim
				""");
		helpMap.put("info",
				"""
				Shows information about the OS
				""");
	}

	public Shell(InputStream inputStream, PrintStream printStream)
	{
		this.in = inputStream;
		this.scanner = new Scanner(this.in);
		this.printStream = printStream;

		this.id = idCounter++;
	}

	@SuppressWarnings("unchecked")
	public void boot()
	{
		System.out.print("\033[H\033[J");
		this.printStream.println("JavaOS booting");

		this.initRootObj();

		this.rootFolder = new Folder("Root");

		class InstallationHelper
		{
			private static void installationProcess(PrintStream printStream, Scanner scanner, JSONObject object)
			{
				String username = prompt("Enter username: ", printStream, scanner);
				object.put("username", username);
				printStream.println("Set username to " + username + ", modify JSON file if you want a different one " +
									"or delete it for a fresh installation");
			}

			private static String prompt(String prompt, PrintStream printStream, Scanner scanner)
			{
				printStream.print(prompt);
				return scanner.nextLine();
			}
		}

		boolean isInstalled = (boolean) this.rootObject.get("isInstalled");
		if (!isInstalled)
		{
			InstallationHelper.installationProcess(this.printStream, this.scanner, this.rootObject);
			this.rootObject.put("isInstalled", true);
			this.printStream.println("Installation process complete!");
		}

		this.username = (String) rootObject.get("username");

		JSONArray root = (JSONArray) rootObject.get("root");
		for (Object folders: root)
		{
			JSONObject folder = (JSONObject) folders;
			this.refreshRootFolderFromJSON(folder, this.rootFolder);
		}

		this.currentFolder = this.rootFolder.getFolder("Home");
		this.updateCurrentPath();
		this.updateJSONFile(this.rootObject.toJSONString());

		this.run();
	}

	private void run()
	{
		boolean shouldReboot = false;

		start:
		while (true)
		{
			String input = this.promptUserInput();

			String[] cmds = input.split(" ");

			String cmd;
			try
			{
				cmd = cmds[0];
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				continue;
			}

			switch (cmd)
			{
				case "help" -> {
					if (cmds.length == 1)
					{
						this.printStream.println("Java OS v0.0.1");
						this.printStream.println("Commands:");
						this.printStream.print(
								"""
								help - Shows this
								help <command> - Shows detailed help for a particular command
								shutdown - Shutdown the OS
								reboot - Reboot the OS
								mkdir - Make a new folder
								mk - Make a new file
								rmdir - Remove the folder with the given name
								rm - Remove the file with the given name
								cd - Change directory into specified folder
								ls - List all subfolders and files
								vim - Open a command line text editor
								info - Prints info about the OS
								jpkg - Package manager (not implemented)
								""");
					}
					else if (cmds.length == 2)
					{
						String command = cmds[1];
						if (!helpMap.containsKey(command))
						{
							this.noSuch("command", command);
							break;
						}

						this.printStream.println("Showing help for command " + command);
						this.printStream.println();
						this.printStream.print(helpMap.get(command));
					}
					else
					{
						this.printStream.print  (ERR);
						this.printStream.println("Wrong usage of help command");
						this.printStream.print  (RESET);
					}
				}

				case "shutdown" -> {
					this.printStream.println("Shutting down...");

					this.printStream.println("Cleaning up resources...");
					System.gc();

					break start;
				}

				case "reboot" -> {
					this.printStream.println("Rebooting...");

					shouldReboot = true;
					break start;
				}

				case "mkdir" -> {
					for (int i = 1; i < cmds.length; ++i)
					{
						Folder added = new Folder(cmds[i], this.currentFolder);

						if (!this.currentFolder.addFolder(added))
						{
							this.printStream.print  (ERR);
							this.printStream.println("Folder already exists");
							this.printStream.print  (RESET);

							continue;
						}

						this.addFolderToJSON(added, this.currentFolder);
					}

					this.updateJSONFile(this.rootObject.toJSONString());
				}

				case "rmdir" -> {
					for (int i = 1; i < cmds.length; ++i)
					{
						if (!this.currentFolder.removeFolder(cmds[i]))
						{
							this.noSuch("folder", cmds[i]);
							continue;
						}

						this.removeFolderFromJSON(cmds[i]);
					}

					this.updateJSONFile(this.rootObject.toJSONString());
				}

				case "mk" -> {
					for (int i = 1; i < cmds.length; ++i)
					{
						File added = new File(cmds[i], this.currentFolder);

						if (!this.currentFolder.addFile(added))
						{
							this.printStream.print  (ERR);
							this.printStream.println("File already exists");
							this.printStream.print  (RESET);

							continue;
						}

						this.addFileToJSON(added, this.currentFolder);
					}

					this.updateJSONFile(this.rootObject.toJSONString());
				}

				case "rm" -> {
					for (int i = 1; i < cmds.length; ++i)
					{
						if (!this.currentFolder.removeFile(cmds[i]))
						{
							this.noSuch("file", cmds[i]);
							continue;
						}

						this.removeFileFromJSON(cmds[i]);
					}

					this.updateJSONFile(this.rootObject.toJSONString());
				}

				case "cd" -> {
					Folder cdFolder;

					try
					{
						String[] paths = cmds[1].split("/");
						cdFolder = this.currentFolder;

						for (int i = 0; i < paths.length; ++i)
						{
							String path = paths[i];

							if (i == 0 && path.equals("~"))
							{
								cdFolder = this.rootFolder.getFolder("Home");
								continue;
							}

							if (path.equals(".."))
							{
								if (cdFolder.equals(this.rootFolder))
								{
									this.printStream.print  (ERR);
									this.printStream.println("Already at topmost directory");
									this.printStream.print  (RESET);
									continue;
								}

								cdFolder = cdFolder.getParentFolder();
								continue;
							}

							cdFolder = cdFolder.getFolder(path);
						}

					} catch (ArrayIndexOutOfBoundsException e)
					{
						this.printStream.print  (ERR);
						this.printStream.println("No folder name provided");
						this.printStream.print  (RESET);
						break;
					} catch (NullPointerException e)
					{
						noSuch("folder", cmds[1]);
						break;
					}

					if (cdFolder == null)
					{
						noSuch("folder", cmds[1]);
						break;
					}

					this.currentFolder = cdFolder;
					this.updateCurrentPath();
				}

				case "ls" -> {
					if (cmds.length > 1)
					{
						String flag = cmds[1];

						if (flag.equals("-tree"))
							this.currentFolder.printTree(this.printStream);

						break;
					}

					this.currentFolder.printNames(this.printStream);
				}

				case "clear" -> System.out.print("\033[H\033[J");

				case "vim" -> {
					if (cmds.length < 2)
					{
						this.printStream.print  (ERR);
						this.printStream.println("No file name provided");
						this.printStream.print  (RESET);
						continue;
					}

				 	final String filePath = cmds[1];
					final String[] filePathSplit = filePath.split("/");
					final String[] filePathSplit2 = new String[filePathSplit.length - 1];
					final String fileName = filePathSplit[filePathSplit.length - 1];
					System.arraycopy(filePathSplit, 0, filePathSplit2, 0, filePathSplit.length - 1);

					Folder fileFolder = this.currentFolder;
					for (String folder: filePathSplit2)
					{
						if (folder.equals(".."))
						{
							fileFolder = fileFolder.getParentFolder();
							continue;
						}

						if (fileFolder.getFolder(folder) != null)
						{
							fileFolder = fileFolder.getFolder(folder);
							continue;
						}

						StringBuilder errName = new StringBuilder();
						for (String _folder: filePathSplit2)
							errName.append(_folder).append('/');
						errName.deleteCharAt(errName.length() - 1);

						noSuch("folder", errName.toString());
						continue start;
					}

					File file;

					if (fileFolder.getFile(fileName) != null)
						file = this.currentFolder.getFile(fileName);
					else
					{
						file = new File(fileName, fileFolder);
						fileFolder.addFile(file);
						this.addFileToJSON(file, fileFolder);
					}

					// Launch vim on file
					this.vim(file);

					this.updateFileToJSON(file);

					this.updateJSONFile(this.rootObject.toJSONString());
				}

				case "info" -> this.printStream.print(
                                    """
									JavaOS version 0.2.4
									Compiled with Java version 16
									
									Package manager: jpkg
									Current number of packages: 0
									"""
								);

				case "jpkg" -> this.printStream.println("Package manager has not been implemented yet");

				case "pwd" -> this.printStream.println(this.currentFullPath);

				default -> {
					if (cmd.isEmpty() || cmd.startsWith("\t") || cmd.startsWith("\033"))
						continue;

					noSuch("command", cmd);
				}
			}
		}

		this.updateJSONFile(this.rootObject.toJSONString());
		if (shouldReboot)
			this.boot();
	}

	private void vim(File file)
	{
		final ArrayList<String> lines = new ArrayList<>(Arrays.asList(file.getContents().split("\\n")));

		final Scanner vimScanner = new Scanner(this.in);

		// Flags to hold anything to print afterwards
		byte flags = 0;

		// Input regexes

		// Write to line
		final Pattern writePattern =    Pattern.compile("(?m)^:w(\\d+) (.+)$");
		// Insert new line and add content
		final Pattern insertPattern =   Pattern.compile("(?m)^:i(\\d+) (.+)$");
		// Remove line
		final Pattern removePattern =   Pattern.compile("(?m)^:r(\\d+)$");
		// Clear line
		final Pattern clearPattern =    Pattern.compile("(?m)^:c(\\d+)$");
		// Save file
		final Pattern savePattern =     Pattern.compile("(?m)^:s$");
		// Quit
		final Pattern quitPattern =     Pattern.compile("(?m)^:q$");

		while (true)
		{
			// Clear screen and move cursor to top left
			System.out.print("\033[H\033[J");

			// Print all lines
			for (int i = 0; i < lines.size(); ++i)
				System.out.println((i + 1) + " - " + lines.get(i));

			// Make 2 lines of space
			for (int i = 0; i < 2; ++i) this.printStream.println();

			// Move cursor to bottom of screen
			this.printStream.print("\033[999E");

			if (flags != 0)
			{
				if ((flags & 0b01) != 0)
				{
					this.printStream.print  ("\033[1F");
					this.printStream.println("File saved");
					this.printStream.print  ("\033[1E");
				}
				else if ((flags & 0b10) != 0)
				{
					this.printStream.print  ("\033[1F");
					this.printStream.println("Unknown command");
					this.printStream.print  ("\033[1E");
				}
			}

			flags = 0;

			// Take user input
			this.printStream.print("> ");
			String input = vimScanner.nextLine();

			Matcher inputMatcher;

			// Match input to regexes
			if ((inputMatcher = writePattern.matcher(input)).matches())
			{
				// Get line number
				final int lineNum = Integer.parseInt(inputMatcher.group(1));

				// Edit line number with the string

				// Add extra lines if it is greater than current lines length
				if (lineNum > lines.size())
					for (int i = lines.size(); i < lineNum; ++i)
						lines.add("");
				lines.set(lineNum - 1, inputMatcher.group(2));
			}
			else if ((inputMatcher = insertPattern.matcher(input)).matches())
			{
				// Insert input string at line num index
				final int lineNum = Integer.parseInt(inputMatcher.group(1));
				lines.add(lineNum, inputMatcher.group(2));
			}
			else if ((inputMatcher = removePattern.matcher(input)).matches())
			{
				final int lineNum = Integer.parseInt(inputMatcher.group(1));
				lines.remove(lineNum - 1);
			}
			else if ((inputMatcher = clearPattern.matcher(input)).matches())
			{
				final int lineNum = Integer.parseInt(inputMatcher.group(1));
				lines.set(lineNum - 1, "");
			}
			else if (savePattern.matcher(input).matches())
			{
				// Save file
				StringBuilder newContents = new StringBuilder();
				for (String line: lines)
					newContents.append(line).append("\n");
				file.setContents(newContents.toString());

				// Print success message
				this.printStream.print("\033[1F");

				flags |= 0b01;
			}
			else if (quitPattern.matcher(input).matches())
				break;
			else
				flags |= 0b10;
		}

		// Clear screen
		System.out.print("\033[H\033[J");
	}

	private void initRootObj()
	{
		try (FileReader reader = new FileReader("osinfo-" + this.id + ".json"))
		{
			JSONParser parser = new JSONParser();

			this.rootObject = (JSONObject) parser.parse(reader);
		}
		catch (ParseException e)
		{
			this.printStream.print  (ERR);
			this.printStream.println("Unable to parse os info data, erasing to default installation...");
			this.printStream.print  (RESET);

			try
			{
				this.rootObject = (JSONObject) new JSONParser().parse(_default);
				this.rootFolder = new Folder("Root");
				this.refreshRootFolderFromJSON(this.rootObject, this.rootFolder);
			}
			catch (ParseException ignored) {}
		}
		catch (FileNotFoundException | NullPointerException e)
		{
			this.printStream.print  (ERR);
			this.printStream.println("No file found for current os info data, creating default installation...");
			this.printStream.print  (RESET);

			try (Writer writer = new FileWriter("osinfo-" + this.id + ".json"))
			{
				writer.write(prettyPrintJSON(_default));
			}
			catch (IOException ignored) {}

			try
			{
				this.rootObject = (JSONObject) new JSONParser().parse(_default);
			}
			catch (ParseException ignored) {}
		}
		catch (IOException ignored) {}
	}

	private void updateCurrentPath()
	{
		this.currentPath = this.currentFolder.getName();
		this.currentFullPath = this.currentFolder.getFullPath();
	}

	private void noSuch(String errName, String thing)
	{
		this.printStream.print  (ERR);
		this.printStream.println("No such " + errName + ": " + thing);
		this.printStream.print  (RESET);
	}

	private String promptUserInput()
	{
		this.printStream.print('[' + username + "@JavaOS " + this.currentPath + "]$ ");
		return this.scanner.nextLine();
	}

	private void updateJSONFile(String JSONString)
	{
		try (Writer writer = new FileWriter("osinfo-" + this.id + ".json"))
		{
			writer.write(prettyPrintJSON(JSONString));
		} catch (IOException ignored) {}
	}

	@SuppressWarnings("unchecked")
	private void refreshRootFolderFromJSON(JSONObject folder, Folder currFolder)
	{
		String folderName = (String) folder.get("name");
		Folder added = new Folder(folderName, currFolder);
		currFolder.addFolder(added);

		JSONArray subFolders = (JSONArray) folder.get("folders");
		if (subFolders != null)
			for (Object folders: subFolders)
			{
				JSONObject subFolder = (JSONObject) folders;
				this.refreshRootFolderFromJSON(subFolder, added);
			}

		JSONArray subFiles = (JSONArray) folder.get("files");
		if (subFiles != null)
			for (Object files: subFiles)
			{
				JSONObject subFile = (JSONObject) files;
				String fileName = ((String) subFile.get("name")) + '.' + subFile.get("type");
				ArrayList<String> fileContents = new ArrayList<String>((JSONArray) subFile.get("contents"));

				File addedFile = new File(fileName, added);
				StringBuilder newContents = new StringBuilder();
				for (String line: fileContents)
					newContents.append(line).append("\n");
				addedFile.setContents(newContents.toString());

				added.addFile(addedFile);
			}
	}

	private JSONObject findJSONFolderInJSONArray(JSONArray arr, String folderName)
	{
		for (Object folders: arr)
		{
			JSONObject folder = (JSONObject) folders;
			if (folder.get("name").equals(folderName))
				return folder;
		}

		return null;
	}

	private JSONObject findJSONFileInJSONArray(JSONArray arr, String fileName)
	{
		String[] fileNameSplit = fileName.split("\\.");
		if (fileNameSplit.length < 2) fileNameSplit = new String[] {fileNameSplit[0], ""};

		for (Object files: arr)
		{
			JSONObject file = (JSONObject) files;
			if (file.get("name").equals(fileNameSplit[0]) && file.get("type").equals(fileNameSplit[1]))
				return file;
		}

		return null;
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private void addFolderToJSON(Folder folder, Folder addedTo)
	{
		if (addedTo.equals(this.rootFolder))
		{
			this.printStream.print  (ERR);
			this.printStream.println("Cannot modify root folder");
			this.printStream.print(  RESET);
			return;
		}

		String[] paths = addedTo.getFullPath().split("/");

		JSONArray currFolder = (JSONArray) this.rootObject.get("root");
		JSONObject currObj;
		for (int i = 0; i < paths.length; ++i)
		{
			if (paths[i].equals("root") && i == 0)
				continue;

			currObj = findJSONFolderInJSONArray(currFolder, paths[i]);
			currFolder = (JSONArray) currObj.get("folders");
		}

		JSONArray object = currFolder;
		object.add(createJSONFolder(folder));
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private void addFileToJSON(File file, Folder addedTo)
	{
		if (addedTo.equals(this.rootFolder))
		{
			this.printStream.print  (ERR);
			this.printStream.println("Cannot modify root folder");
			this.printStream.print  (RESET);
			return;
		}

		String[] paths = addedTo.getFullPath().split("/");

		JSONArray currFolder = (JSONArray) this.rootObject.get("root");
		JSONObject currObj;
		for (int i = 0; i < paths.length; ++i)
		{
			if (paths[i].equals("root") && i == 0)
				continue;

			currObj = findJSONFolderInJSONArray(currFolder, paths[i]);

			if (i != paths.length - 1)
				currFolder = (JSONArray) currObj.get("folders");
			else
				currFolder = (JSONArray) currObj.get("files");
		}

		JSONArray object = currFolder;
		object.add(createJSONFile(file));
	}

	@SuppressWarnings({"unchecked", "ConstantConditions"})
	private void updateFileToJSON(File file)
	{
		String[] paths = this.currentFullPath.split("/");

		JSONArray currFolder = (JSONArray) this.rootObject.get("root");
		JSONObject currObj = null;
		for (int i = 0; i < paths.length; ++i)
		{
			if (paths[i].equals("root") && i == 0)
				continue;

			currObj = findJSONFolderInJSONArray(currFolder, paths[i]);
			currFolder = (JSONArray) currObj.get("folders");
		}

		JSONObject file1 = findJSONFileInJSONArray((JSONArray) currObj.get("files"), file.getFullName());

		file1.put("contents", file.getContents());
	}

	@SuppressWarnings("unchecked")
	private static JSONObject createJSONFolder(Folder folder)
	{
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("name", folder.getName());

		// Assumes folder has no subfolders or files
		jsonObject.put("folders", new JSONArray());
		jsonObject.put("files", new JSONArray());

		return jsonObject;
	}

	@SuppressWarnings("unchecked")
	private static JSONObject createJSONFile(File file)
	{
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("name", file.getName());
		jsonObject.put("type", file.getType());

		jsonObject.put("contents", file.getContents());

		return jsonObject;
	}

	@SuppressWarnings({"ConstantConditions"})
	private void removeFolderFromJSON(String name)
	{
		if (this.currentFolder.equals(this.rootFolder))
		{
			this.printStream.print  (ERR);
			this.printStream.println("Cannot modify root folder");
			this.printStream.print  (RESET);
			return;
		}

		String[] paths = this.currentFullPath.split("/");

		JSONArray currFolder = (JSONArray) this.rootObject.get("root");
		JSONObject currObj;
		for (int i = 0; i < paths.length; ++i)
		{
			if (paths[i].equals("root") && i == 0)
				continue;

			currObj = findJSONFolderInJSONArray(currFolder, paths[i]);
			currFolder = (JSONArray) currObj.get("folders");
		}

		JSONArray object = currFolder;

		int i;
		for (i = 0; i < object.size(); i++)
		{
			JSONObject folder1 = (JSONObject) object.get(i);
			String folderName = (String) folder1.get("name");

			if (folderName.equals(name))
				break;
		}

		object.remove(i);
	}

	@SuppressWarnings({"ConstantConditions"})
	private void removeFileFromJSON(String name)
	{
		// This should ideally never run if user has not tampered with the JSON, but put as a safety check anyways
		if (this.currentFolder.equals(this.rootFolder))
		{
			this.printStream.print  (ERR);
			this.printStream.println("Cannot modify root folder");
			this.printStream.print  (RESET);
			return;
		}

		String[] paths = this.currentFullPath.split("/");

		JSONArray currFolder = (JSONArray) this.rootObject.get("root");
		JSONObject currObj;
		for (int i = 0; i < paths.length; ++i)
		{
			if (paths[i].equals("root") && i == 0)
				continue;

			currObj = findJSONFolderInJSONArray(currFolder, paths[i]);

			if (i != paths.length - 1)
				currFolder = (JSONArray) currObj.get("folders");
			else
				currFolder = (JSONArray) currObj.get("files");
		}

		JSONArray object = currFolder;

		int i;
		for (i = 0; i < object.size(); i++)
		{
			JSONObject file = (JSONObject) object.get(i);
			String fileName = (String) file.get("name") + '.' + file.get("type");

			if (fileName.equals(name))
				break;
		}

		object.remove(i);
	}

	/**
	 * A simple implementation to pretty-print JSON file.
	 *
	 * @param unformattedJsonString The unformatted JSON string
	 * @return Pretty-ified JSON string
	 */
	private static String prettyPrintJSON(String unformattedJsonString)
	{
		StringBuilder prettyJSONBuilder = new StringBuilder();
		int indentLevel = 0;
		boolean inQuote = false;
		for(char charFromUnformattedJson : unformattedJsonString.toCharArray()) {
			switch(charFromUnformattedJson) {
				case '"':
					// switch the quoting status
					inQuote = !inQuote;
					prettyJSONBuilder.append(charFromUnformattedJson);
					break;
				case ' ':
					// For space: ignore the space if it is not being quoted.
					if(inQuote) {
						prettyJSONBuilder.append(charFromUnformattedJson);
					}
					break;
				case '{':
				case '[':
					// Starting a new block: increase the indent level
					prettyJSONBuilder.append(charFromUnformattedJson);
					indentLevel++;
					appendIndentedNewLine(indentLevel, prettyJSONBuilder);
					break;
				case '}':
				case ']':
					// Ending a new block; decrease the indent level
					indentLevel--;
					appendIndentedNewLine(indentLevel, prettyJSONBuilder);
					prettyJSONBuilder.append(charFromUnformattedJson);
					break;
				case ',':
					// Ending a json item; create a new line after
					prettyJSONBuilder.append(charFromUnformattedJson);
					if(!inQuote) {
						appendIndentedNewLine(indentLevel, prettyJSONBuilder);
					}
					break;
				case ':':
					prettyJSONBuilder.append(charFromUnformattedJson);
					if (!inQuote)
						prettyJSONBuilder.append(' ');
					break;
				default:
					prettyJSONBuilder.append(charFromUnformattedJson);
			}
		}
		return prettyJSONBuilder.toString();
	}

	/**
	 * Print a new line with indention at the beginning of the new line.
	 * @param indentLevel The indent level
	 * @param stringBuilder StringBuilder to append to
	 */
	private static void appendIndentedNewLine(int indentLevel, StringBuilder stringBuilder)
	{
		stringBuilder.append("\n");
		stringBuilder.append("  ".repeat(Math.max(0, indentLevel)));
	}
}
