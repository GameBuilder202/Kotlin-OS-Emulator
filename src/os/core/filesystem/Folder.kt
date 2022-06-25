package os.core.filesystem

import java.io.PrintStream

class Folder constructor(var name: String, val parentFolder: Folder? = null)
{
	private val files: ArrayList<File> = ArrayList()
	private val folders: ArrayList<Folder> = ArrayList()

	val fullPath: String
		get()
		{
			var pathFolder = this
			var parentFolder: Folder?
			val fullPath = StringBuilder()
			while (pathFolder.parentFolder.also { parentFolder = it } != null)
			{
				fullPath.insert(0, '/'.toString() + pathFolder.name)
				pathFolder = parentFolder!!
			}
			fullPath.insert(0, "root")
			return fullPath.toString()
		}

	fun addFile(file: File): Boolean
	{
		for (file1 in files) if (file1 == file)
		{
			return false
		}
		return files.add(file)
	}

	fun getFile(name: String): File?
	{
		for (file in files) if (file.fullName == name) return file
		return null
	}

	fun removeFile(name: String): Boolean
	{
		return files.remove(getFile(name))
	}

	fun addFolder(folder: Folder): Boolean
	{
		for (folder1 in folders) if (folder1 == folder) return false
		return folders.add(folder)
	}

	fun getFolder(name: String): Folder?
	{
		for (folder in folders) if (folder.name == name) return folder
		return null
	}

	fun removeFolder(name: String): Boolean
	{
		return folders.remove(getFolder(name))
	}

	fun rename(newName: String)
	{
		name = newName
	}

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other == null || javaClass != other.javaClass) return false
		val folder = other as Folder
		return name == folder.name
	}

	fun printTree(printStream: PrintStream)
	{
		this.printTree(0, printStream)
	}

	private fun printTree(tabCount: Int, printStream: PrintStream)
	{
		printStream.println("  ".repeat(tabCount) + "$name/")
		for (folder in folders)
			folder.printTree(tabCount + 1, printStream)
		for (file in files)
			printStream.println("  ".repeat(tabCount) + file.fullName)
	}

	fun printNames(printStream: PrintStream)
	{
		for (folder in folders) printStream.println("${folder.name}/")
		for (file in files)     printStream.println(file.fullName)
	}
}
