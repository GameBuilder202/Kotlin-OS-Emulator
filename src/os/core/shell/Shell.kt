@file:Suppress("MemberVisibilityCanBePrivate")

package os.core.shell

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import org.kohsuke.github.GHContent
import org.kohsuke.github.GHFileNotFoundException
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import os.core.filesystem.File
import os.core.filesystem.Folder
import java.io.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

class Shell(private val `in`: InputStream, private val printStream: PrintStream)
{
	private val scanner: Scanner = Scanner(`in`)
	private var currentPath = "~"
	private var currentFullPath: String = ""
	private var currentFolder: Folder? = null
	private val rootFolder: Folder = Folder("root", null)
	private var username: String = ""
	private var rootObject: JSONObject = JSONObject()

	companion object
	{
		private val default: String =
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
				  "name": "packages",
				  "folders": [],
				  "files": []
				}
			  ],

			  "username": ""
			}
			"""
				.trimIndent()
				.replace("\t", "")
				.replace("\n", "")
				.replace(" ", "")

		private val helpMap: HashMap<String?, String> = HashMap()
		private val repo: GHRepository = GitHub.connectAnonymously().getRepository("GameBuilder202/Java-OS-Packages")!!

		init
		{
			helpMap["help"] =
				"""
				help - Shows help for all commands
				help <command> - Shows detailed help for a particular command
				""".trimIndent()
			helpMap["shutdown"] =
				"""
				Shutdown the OS
				Additionally, runs the garbage collector to clean up resources, and updates os info json file
				""".trimIndent()
			helpMap["reboot"] =
				"""
				Reboots the OS
				Additionally, runs the garbage collector to clean up resources, and updates os info json file
				""".trimIndent()
			helpMap["mkdir"] =
				"""
				Makes a new directory relative to the current directory
				Subdirectories are separated with /
				Spaces separate different folders to be created
				""".trimIndent()
			helpMap["mk"] =
				"""
				Makes a new file relative to the current directory
				Subdirectories are separated with /
				Spaces separate different files to be created
				""".trimIndent()
			helpMap["rmdir"] =
				"""
				Deletes a folder relative to the current directory
				Subdirectories are separated with /
				Spaces separate different folders to be deleted
				""".trimIndent()
			helpMap["rm"] =
				"""
				Deletes a file relative to the current directory
				Subdirectories are separated with /
				Spaces separate different files to be deleted
				""".trimIndent()
			helpMap["cd"] =
				"""
				Change directory into a subfolder
				To change directory relative to Home folder, use ~ at the start
				""".trimIndent()
			helpMap["ls"] =
				"""
				List all files and folders under the current directory
				Use flag -tree to list in a tree format with all sub-files and sub-folders
				""".trimIndent()
			helpMap["clear"] = "Clears the screen"
			helpMap["vim"] =
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
				""".trimIndent()
			helpMap["info"] = "Shows information about the OS"
			helpMap["jpkg"] =
				"""
				JavaOS Package manager
				All packages are installed to root/packages folder
				Commands are:
				install <name[@version]> - Install a package with the version if specified
				remove <name>            - Remove a package
				list                     - List all packages
				""".trimIndent()
			helpMap["pwd"] =
				"""
				Prints the current working directory
				This is usually the current directory
				""".trimIndent()
		}

		private fun createJSONFolder(folder: Folder): JSONObject
		{
			val jsonObject = JSONObject()
			jsonObject["name"] = folder.name

			// Assumes folder has no subfolders or files
			jsonObject["folders"] = JSONArray()
			jsonObject["files"] = JSONArray()
			return jsonObject
		}

		private fun createJSONFile(file: File): JSONObject
		{
			val jsonObject = JSONObject()
			jsonObject["name"] = file.name
			jsonObject["type"] = file.type
			jsonObject["contents"] = file.contents
			return jsonObject
		}

		/**
		 * A simple implementation to pretty-print JSON file.
		 *
		 * @param unformattedJsonString The unformatted JSON string
		 * @return Pretty-ified JSON string
		 */
		private fun prettyPrintJSON(unformattedJsonString: String): String
		{
			val prettyJSONBuilder = StringBuilder()
			var indentLevel = 0
			var inQuote = false
			for (charFromUnformattedJson: Char in unformattedJsonString.toCharArray())
			{
				when (charFromUnformattedJson)
				{
					'"'      ->
					{
						// switch the quoting status
						inQuote = !inQuote
						prettyJSONBuilder.append(charFromUnformattedJson)
					}
					' '      ->                    // For space: ignore the space if it is not being quoted.
						if (inQuote)
						{
							prettyJSONBuilder.append(charFromUnformattedJson)
						}
					'{', '[' ->
					{
						// Starting a new block: increase the indent level
						prettyJSONBuilder.append(charFromUnformattedJson)
						indentLevel++
						appendIndentedNewLine(indentLevel, prettyJSONBuilder)
					}
					'}', ']' ->
					{
						// Ending a new block; decrease the indent level
						indentLevel--
						appendIndentedNewLine(indentLevel, prettyJSONBuilder)
						prettyJSONBuilder.append(charFromUnformattedJson)
					}
					','      ->
					{
						// Ending a json item; create a new line after
						prettyJSONBuilder.append(charFromUnformattedJson)
						if (!inQuote)
						{
							appendIndentedNewLine(indentLevel, prettyJSONBuilder)
						}
					}
					':'      ->
					{
						prettyJSONBuilder.append(charFromUnformattedJson)
						if (!inQuote) prettyJSONBuilder.append(' ')
					}
					else     -> prettyJSONBuilder.append(charFromUnformattedJson)
				}
			}
			return prettyJSONBuilder.toString()
		}

		/**
		 * Print a new line with indention at the beginning of the new line.
		 * @param indentLevel The indent level
		 * @param stringBuilder StringBuilder to append to
		 */
		private fun appendIndentedNewLine(indentLevel: Int, stringBuilder: StringBuilder)
		{
			stringBuilder.append("\n")
			stringBuilder.append("  ".repeat(0.coerceAtLeast(indentLevel)))
		}
	}

	fun bootNoRun()
	{
		printStream.println("JavaOS booting")
		initRootObj()
		val installationHelper = object
		{
			fun installationProcess(printStream: PrintStream, scanner: Scanner, `object`: JSONObject?)
			{
				val username = prompt("Enter username: ", printStream, scanner)
				`object`!!["username"] = username
				printStream.println(
					"Set username to $username, modify JSON file if you want a different one " +
							"or delete it for a fresh installation"
				)
			}

			private fun prompt(prompt: String, printStream: PrintStream, scanner: Scanner): String
			{
				printStream.print(prompt)
				return scanner.nextLine()
			}
		}

		val isInstalled = rootObject["isInstalled"] as Boolean
		if (!isInstalled)
		{
			installationHelper.installationProcess(printStream, scanner, rootObject)
			rootObject["isInstalled"] = true
			printStream.println("Installation process complete!")
		}
		username = rootObject["username"] as String
		val root = rootObject["root"] as JSONArray
		for (folders: Any? in root)
		{
			val folder = folders as JSONObject
			refreshRootFolderFromJSON(folder, rootFolder)
		}
		currentFolder = rootFolder.getFolder("Home")
		updateCurrentPath()
		updateJSONFile(rootObject.toJSONString())
	}

	fun boot()
	{
		print("\u001b[H\u001b[2J\u001b[3J")
		bootNoRun()
		this.run()
	}

	private fun run()
	{
		var shouldReboot = false

		start@ while (true)
		{
			val input = promptUserInput()
			val cmds = input.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			if (cmds.isEmpty()) continue

			val args = cmds.copyOfRange(1, cmds.size)
			val cmd: String
			try
			{
				cmd = cmds[0]
			}
			catch (e: ArrayIndexOutOfBoundsException)
			{
				continue
			}

			when (cmd)
			{
				"help"     -> help(args)
				"shutdown" ->
				{
					printStream.println("Shutting down...")
					printStream.println("Cleaning up resources...")
					System.gc()
					break@start
				}
				"reboot"   ->
				{
					printStream.println("Rebooting...")
					shouldReboot = true
					break@start
				}
				"mkdir"    -> mkdir(args)
				"rmdir"    -> rmdir(args)
				"mk"       -> mk(args)
				"rm"       -> rm(args)
				"cd"       -> cd(args)
				"ls"       -> ls(args)
				"clear"    -> print("\u001b[H\u001b[2J\u001b[3J")
				"vim"      -> vim(args)
				"info"     ->
				{
					if (args.isNotEmpty())
					{
						printStream.println("info does not take any arguments")
						break
					}
					info()
				}
				"jpkg"     -> jpkg(args)
				"pwd"      ->
				{
					if (args.isNotEmpty())
					{
						printStream.println("pwd does not take any arguments")
						break
					}
					pwd()
				}
				else       ->
				{
					if (cmd.startsWith("\t") || cmd.startsWith("\u001b")) continue
					noSuch("command", cmd)
				}
			}
		}
		updateJSONFile(rootObject.toJSONString())
		if (shouldReboot) boot()
	}

	fun help(args: Array<String>)
	{
		if (args.isEmpty())
		{
			printStream.println("Java OS v0.4.0")
			printStream.println("Commands:")
			printStream.print(
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
				jpkg - Package manager
				pwd - Prints the working directory
				""".trimIndent()
			)
		}
		else if (args.size == 1)
		{
			val command = args[0]
			if (!helpMap.containsKey(command))
			{
				noSuch("command", command)
				return
			}
			printStream.println("Showing help for command $command")
			printStream.println()
			printStream.print(helpMap[command])
		}
		else printStream.println("Wrong usage of help command")
	}

	fun mkdir(args: Array<String>)
	{
		for (arg: String in args)
		{
			val added = Folder(arg, currentFolder)
			if (!currentFolder!!.addFolder(added))
			{
				printStream.println("Folder " + added.name + " already exists")
				continue
			}
			addFolderToJSON(added, currentFolder!!)
		}
		updateJSONFile(rootObject.toJSONString())
	}

	fun rmdir(args: Array<String>)
	{
		for (arg: String in args)
		{
			if (!currentFolder!!.removeFolder(arg))
			{
				noSuch("folder", arg)
				continue
			}
			removeFolderFromJSON(arg)
		}
		updateJSONFile(rootObject.toJSONString())
	}

	fun mk(args: Array<String>)
	{
		for (arg: String in args)
		{
			val added = File(arg, currentFolder!!)
			if (!currentFolder!!.addFile(added))
			{
				printStream.println("File already exists")
				continue
			}
			addFileToJSON(added, currentFolder!!)
		}
		updateJSONFile(rootObject.toJSONString())
	}

	fun rm(args: Array<String>)
	{
		for (arg: String? in args)
		{
			if (!currentFolder!!.removeFile(arg!!))
			{
				noSuch("file", arg)
				continue
			}
			removeFileFromJSON(arg)
		}
		updateJSONFile(rootObject.toJSONString())
	}

	fun cd(args: Array<String>)
	{
		var cdFolder: Folder?
		try
		{
			val paths = args[0].split("/".toRegex()).dropLastWhile { it.isEmpty() }
				.toTypedArray()
			cdFolder = currentFolder
			for (i in paths.indices)
			{
				val path = paths[i]
				if (i == 0 && (path == "~"))
				{
					cdFolder = rootFolder.getFolder("Home")
					continue
				}
				if ((path == ".."))
				{
					if ((cdFolder == rootFolder))
					{
						printStream.println("Already at topmost directory")
						continue
					}
					cdFolder = cdFolder!!.parentFolder
					continue
				}
				cdFolder = cdFolder!!.getFolder(path)
			}
		}
		catch (e: ArrayIndexOutOfBoundsException)
		{
			printStream.println("No folder name provided")
			return
		}
		catch (e: NullPointerException)
		{
			noSuch("folder", args[0])
			return
		}
		if (cdFolder == null)
		{
			noSuch("folder", args[0])
			return
		}
		currentFolder = cdFolder
		updateCurrentPath()
	}

	fun ls(args: Array<String>)
	{
		if (args.size > 1)
		{
			val flag = args[0]
			if (flag == "-tree") currentFolder!!.printTree(printStream)
			return
		}
		else
			currentFolder!!.printNames(printStream)
	}

	fun info()
	{
		printStream.print(
			"""
			JavaOS version 0.4.0
			Compiled with Kotlin version 1.7.0
			
			Package manager: jpkg
			""".trimIndent()
		)
	}

	fun jpkg(args: Array<String>)
	{
		try
		{
			if (args.isEmpty())
			{
				printStream.println("No operation provided")
				return
			}
			when (args[0])
			{
				"install" ->
				{
					if (args.size < 2)
					{
						printStream.println("No package name provided")
						return
					}
					val packageInput = args[1].split("@".toRegex()).dropLastWhile { it.isEmpty() }
						.toTypedArray()
					val packageName = packageInput[0]
					val versionGiven = packageInput.size == 2
					val versionMatcher = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")
					if (rootFolder.getFolder("packages")!!.getFolder(packageName) != null)
					{
						printStream.println("Package already installed, aborting installation")
						return
					}
					printStream.println("Getting package...")
					try
					{
						val parser = JSONParser()
						val content = repo.getDirectoryContent("$packageName/")
						var versionFile: GHContent? = null
						for (file: GHContent in content) if ((file.name == "versions.json")) versionFile = file
						if (versionFile == null)
						{
							printStream.println("No versions.json file found in package")
							return
						}
						printStream.println("Found package, parsing data...")
						val json = parser.parse(String(versionFile.read().readAllBytes())) as JSONObject
						val version = if (versionGiven) packageInput[1] else (json["latest"] as String?)!!
						if (!versionMatcher.matcher(version).matches())
						{
							printStream.println("Invalid version as input, contact package owner if you did not provide version")
							return
						}
						var file: GHContent? = null
						for (fileToCheck: GHContent in content) if ((fileToCheck.name == "$version.json")) file =
							fileToCheck
						if (file == null)
						{
							printStream.println("Version $version of package $packageName not found")
							return
						}
						printStream.println("Found package, installing...")
						val packageFiles = parser.parse(String(file.read().readAllBytes())) as JSONArray
						val packageFolder = Folder(packageName, rootFolder.getFolder("packages"))
						rootFolder.getFolder("packages")!!.addFolder(packageFolder)
						addFolderToJSON(packageFolder, rootFolder.getFolder("packages")!!)
						run {
							@Suppress("SpellCheckingInspection") val vversionFile = File("VERSION", packageFolder)
							vversionFile.contents = version
							packageFolder.addFile(vversionFile)
							this.addFileToJSON(vversionFile, packageFolder)
						}
						for (fileToInstall: Any? in packageFiles)
						{
							val fileToInstallJSON = fileToInstall as JSONObject
							val fileName = fileToInstallJSON["name"] as String + '.' + fileToInstallJSON["type"]
							val fileContents = fileToInstallJSON["contents"] as String
							val installFile = File(fileName, packageFolder)
							installFile.contents = fileContents
							packageFolder.addFile(installFile)
							addFileToJSON(installFile, packageFolder)
						}
						updateJSONFile(rootObject.toJSONString())
						printStream.println("Package installed successfully")
					}
					catch (e: GHFileNotFoundException)
					{
						noSuch("package", packageName)
					}
					catch (ignored: ParseException)
					{
					}
				}
				"list"    ->
				{
					val packageFolder = rootFolder.getFolder("packages")
					try
					{
						// Use reflection to get and list all subfolders of packages class
						val f = packageFolder!!.javaClass.getDeclaredField("folders")
						f.isAccessible = true
						@Suppress("UNCHECKED_CAST")
						val folders = f[packageFolder] as ArrayList<Folder>
						for (folder: Folder in folders) printStream.println(folder.name)
					}
					catch (ignored: NoSuchFieldException)   {}
					catch (ignored: IllegalAccessException) {}
				}
				else      ->
				{
					if (args[0].isEmpty() || args[0].startsWith("\t") || args[0].startsWith("\u001b")) return
					noSuch("command", args[0])
				}
			}
		}
		catch (ignored: IOException)
		{
		}
	}

	fun pwd()
	{
		printStream.println(currentFullPath)
	}

	private fun vim(args: Array<String>)
	{
		if (args.isEmpty())
		{
			printStream.println("No file name provided")
			return
		}
		val filePath = args[0]
		val filePathSplit = filePath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		val filePathSplit2 = filePathSplit.copyOfRange(0, filePathSplit.size - 1)
		val fileName = filePathSplit[filePathSplit.size - 1]
		var fileFolder = currentFolder
		for (folder: String in filePathSplit2)
		{
			if ((folder == ".."))
			{
				fileFolder = fileFolder!!.parentFolder
				continue
			}
			if (fileFolder!!.getFolder(folder) != null)
			{
				fileFolder = fileFolder.getFolder(folder)
				continue
			}
			val errName = StringBuilder()
			for (_folder: String? in filePathSplit2) errName.append(_folder).append('/')
			errName.deleteCharAt(errName.length - 1)
			noSuch("folder", errName.toString())
			return
		}
		val file: File
		if (fileFolder!!.getFile(fileName) != null) file = currentFolder!!.getFile(fileName)!!
		else
		{
			file = File(fileName, fileFolder)
			fileFolder.addFile(file)
			addFileToJSON(file, fileFolder)
		}
		val lines = ArrayList(listOf(*file.contents
			.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()))
		val vimScanner = Scanner(`in`)

		// Flags to hold anything to print afterwards
		var flags: Byte = 0

		// Input regexes

		// Write to line
		val writePattern = Pattern.compile("(?m)^:w(\\d+) (.+)$")
		// Insert new line and add content
		val insertPattern = Pattern.compile("(?m)^:i(\\d+) (.+)$")
		// Remove line
		val removePattern = Pattern.compile("(?m)^:r(\\d+)$")
		// Clear line
		val clearPattern = Pattern.compile("(?m)^:c(\\d+)$")
		// Save file
		val savePattern = Pattern.compile("(?m)^:s$")
		// Quit
		val quitPattern = Pattern.compile("(?m)^:q$")
		while (true)
		{
			// Clear screen and move cursor to top left
			print("\u001b[H\u001b[J")

			// Print all lines
			for (i in lines.indices) println((i + 1).toString() + " - " + lines[i])

			// Make 2 lines of space
			for (i in 0..1) printStream.println()

			// Move cursor to bottom of screen
			printStream.print("\u001b[999E")
			if (flags.toInt() != 0)
			{
				if ((flags.toInt() and 1) != 0)
				{
					printStream.print("\u001b[1F")
					printStream.println("File saved")
					printStream.print("\u001b[1E")
				}
				else if ((flags.toInt() and 2) != 0)
				{
					printStream.print("\u001b[1F")
					printStream.println("Unknown command")
					printStream.print("\u001b[1E")
				}
			}
			flags = 0

			// Take user input
			printStream.print("> ")
			val input = vimScanner.nextLine()
			var inputMatcher: Matcher

			// Match input to regexes
			if ((writePattern.matcher(input).also { inputMatcher = it }).matches())
			{
				// Get line number
				val lineNum = inputMatcher.group(1).toInt()

				// Edit line number with the string

				// Add extra lines if it is greater than current lines length
				if (lineNum > lines.size) for (i in lines.size until lineNum) lines.add("")
				lines[lineNum - 1] = inputMatcher.group(2)
			}
			else if ((insertPattern.matcher(input).also { inputMatcher = it }).matches())
			{
				// Insert input string at line num index
				val lineNum = inputMatcher.group(1).toInt()
				lines.add(lineNum, inputMatcher.group(2))
			}
			else if ((removePattern.matcher(input).also { inputMatcher = it }).matches())
			{
				val lineNum = inputMatcher.group(1).toInt()
				lines.removeAt(lineNum - 1)
			}
			else if ((clearPattern.matcher(input).also { inputMatcher = it }).matches())
			{
				val lineNum = inputMatcher.group(1).toInt()
				lines[lineNum - 1] = ""
			}
			else if (savePattern.matcher(input).matches())
			{
				// Save file
				val newContents = StringBuilder()
				for (line: String in lines) newContents.append(line).append("\n")
				file.contents = newContents.toString()

				// Print success message
				printStream.print("\u001b[1F")
				flags = (flags.toInt() or 1).toByte()
			}
			else if (quitPattern.matcher(input).matches()) break
			else
				flags = (flags.toInt() or 2).toByte()
		}
		updateFileToJSON(file)
		updateJSONFile(rootObject.toJSONString())
	}

	private fun initRootObj()
	{
		try
		{
			FileReader("osinfo.json").use { reader ->
				val parser = JSONParser()
				rootObject = parser.parse(reader) as JSONObject
			}
		}
		catch (e: ParseException)
		{
			printStream.println("Unable to parse os info data, erasing to default installation...")
			try
			{
				rootObject = JSONParser().parse(default) as JSONObject
				refreshRootFolderFromJSON(rootObject, rootFolder)
			}
			catch (ignored: ParseException) {}
		}
		catch (e: FileNotFoundException)
		{
			printStream.println("No file found for current os info data, creating default installation...")
			try
			{
				FileWriter("osinfo.json").use { writer -> writer.write(prettyPrintJSON(default)) }
			}
			catch (ignored: IOException) {}
			try
			{
				rootObject = JSONParser().parse(default) as JSONObject
			}
			catch (ignored: ParseException) {}
		}
		catch (e: NullPointerException)
		{
			printStream.println("No file found for current os info data, creating default installation...")
			try
			{
				FileWriter("osinfo.json").use { writer -> writer.write(prettyPrintJSON(default)) }
			}
			catch (ignored: IOException)
			{
			}
			try
			{
				rootObject = JSONParser().parse(default) as JSONObject
			}
			catch (ignored: ParseException)
			{
			}
		}
		catch (ignored: IOException)
		{
		}
	}

	private fun updateCurrentPath()
	{
		currentPath = currentFolder!!.name
		currentFullPath = currentFolder!!.fullPath
	}

	private fun noSuch(errName: String, thing: String)
	{
		printStream.println("No such $errName: $thing")
	}

	private fun promptUserInput(): String
	{
		printStream.print("[$username@KtOS $currentPath]$ ")

		while (!scanner.hasNextLine()) Thread.sleep(100)
		return scanner.nextLine()
	}

	private fun updateJSONFile(JSONString: String)
	{
		try
		{
			FileWriter("osinfo.json").use { writer ->
				writer.write(
					prettyPrintJSON(
						JSONString
					)
				)
			}
		}
		catch (ignored: IOException) {}
	}

	private fun refreshRootFolderFromJSON(folder: JSONObject, currFolder: Folder)
	{
		val folderName = folder["name"] as String
		val added = Folder(folderName, currFolder)
		currFolder.addFolder(added)
		val subFolders = folder["folders"] as JSONArray
		for (folders: Any? in subFolders)
		{
			val subFolder = folders as JSONObject
			refreshRootFolderFromJSON(subFolder, added)
		}
		val subFiles = folder["files"] as JSONArray
		for (files: Any? in subFiles)
		{
			val subFile = files as JSONObject
			val fileName = (subFile["name"] as String) + '.' + subFile["type"]
			val fileContents = subFile["contents"] as String
			val addedFile = File(fileName, added)
			addedFile.contents = fileContents
			added.addFile(addedFile)
		}
	}

	private fun findJSONFolderInJSONArray(arr: JSONArray, folderName: String): JSONObject?
	{
		for (folders: Any? in arr)
		{
			val folder = folders as JSONObject
			if ((folder["name"] == folderName)) return folder
		}
		return null
	}

	private fun findJSONFileInJSONArray(arr: JSONArray, fileName: String): JSONObject?
	{
		var fileNameSplit = fileName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		if (fileNameSplit.size < 2) fileNameSplit = arrayOf(fileNameSplit[0], "")
		for (files: Any? in arr)
		{
			val file = files as JSONObject
			if ((file["name"] == fileNameSplit[0]) && (file["type"] == fileNameSplit[1])) return file
		}
		return null
	}

	private fun addFolderToJSON(folder: Folder, addedTo: Folder)
	{
		if (addedTo == rootFolder)
		{
			printStream.println("Cannot modify root folder")
			return
		}
		val paths = addedTo.fullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		var currFolder = rootObject["root"] as JSONArray
		var currObj: JSONObject?
		for (i in paths.indices)
		{
			if ((paths[i] == "root") && i == 0) continue
			currObj = findJSONFolderInJSONArray(currFolder, paths[i])
			currFolder = currObj!!["folders"] as JSONArray
		}
		val `object` = currFolder
		`object`.add(createJSONFolder(folder))
	}

	private fun addFileToJSON(file: File, addedTo: Folder)
	{
		if (addedTo == rootFolder)
		{
			printStream.println("Cannot modify root folder")
			return
		}
		val paths = addedTo.fullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		var currFolder = rootObject["root"] as JSONArray
		var currObj: JSONObject?
		for (i in paths.indices)
		{
			if ((paths[i] == "root") && i == 0) continue
			currObj = findJSONFolderInJSONArray(currFolder, paths[i])
			currFolder = if (i != paths.size - 1) currObj!!["folders"] as JSONArray
			else currObj!!["files"] as JSONArray
		}
		val `object` = currFolder
		`object`.add(createJSONFile(file))
	}

	private fun updateFileToJSON(file: File)
	{
		val paths = currentFullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		var currFolder = rootObject["root"] as JSONArray
		var currObj: JSONObject? = null
		for (i in paths.indices)
		{
			if ((paths[i] == "root") && i == 0) continue
			currObj = findJSONFolderInJSONArray(currFolder, paths[i])
			currFolder = currObj!!["folders"] as JSONArray
		}
		val file1 = findJSONFileInJSONArray(currObj!!["files"] as JSONArray, file.fullName)
		file1!!["contents"] = file.contents
	}

	private fun removeFolderFromJSON(name: String)
	{
		if (currentFolder == rootFolder)
		{
			printStream.println("Cannot modify root folder")
			return
		}
		val paths = currentFullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		var currFolder = rootObject["root"] as JSONArray
		var currObj: JSONObject?
		for (i in paths.indices)
		{
			if ((paths[i] == "root") && i == 0) continue
			currObj = findJSONFolderInJSONArray(currFolder, paths[i])
			currFolder = currObj!!["folders"] as JSONArray
		}
		val `object` = currFolder
		var i = 0
		while (i < `object`.size)
		{
			val folder1 = `object`[i] as JSONObject
			val folderName = folder1["name"] as String
			if ((folderName == name)) break
			i++
		}

		if (i == `object`.size)
		{
			noSuch("folder", name)
			return
		}

		`object`.removeAt(i)
	}

	private fun removeFileFromJSON(name: String)
	{
		// This should ideally never run if user has not tampered with the JSON, but put as a safety check anyways
		if (currentFolder == rootFolder)
		{
			printStream.println("Cannot modify root folder")
			return
		}
		val paths = currentFullPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }
			.toTypedArray()
		var currFolder = rootObject["root"] as JSONArray
		var currObj: JSONObject?
		for (i in paths.indices)
		{
			if ((paths[i] == "root") && i == 0) continue
			currObj = findJSONFolderInJSONArray(currFolder, paths[i])
			currFolder = if (i != paths.size - 1) currObj!!["folders"] as JSONArray
			else currObj!!["files"] as JSONArray
		}
		val `object` = currFolder
		var i = 0
		while (i < `object`.size)
		{
			val file = `object`[i] as JSONObject
			val fileName = file["name"] as String + (if (file["type"] != "") "." + file["type"] as String else "")
			if (fileName == name) break
			i++
		}

		if (i == `object`.size)
		{
			noSuch("file", name)
			return
		}

		`object`.removeAt(i)
	}
}
