package os.core.filesystem

class File(name: String, parentFolder: Folder)
{
	var name: String
		private set
	var type: String
		private set
	var contents: String
	val parentFolder: Folder

	init
	{
		@Suppress("SpellCheckingInspection")
		val nname = decodeName(name)
		this.name = nname[0]
		type = nname[1]
		contents = ""
		this.parentFolder = parentFolder
	}

	fun rename(newName: String)
	{
		@Suppress("SpellCheckingInspection")
		val nname = decodeName(newName)
		name = nname[0]
		type = nname[1]
	}

	val fullName: String
		get()
		= if (type.isNotEmpty()) "$name.$type" else name

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other == null || javaClass != other.javaClass) return false
		val file = other as File
		return fullName == file.fullName
	}

	override fun toString(): String
	{
		return fullName
	}

	companion object
	{
		private fun decodeName(name: String): Array<String>
		{
			val sub = name.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			if (sub.size == 1) return arrayOf(sub[0], "")
			val out = arrayOf("", "")
			for (i in 0 until sub.size - 1)
			{
				out[0] += sub[i]
			}
			out[1] = sub[sub.size - 1]

			return out
		}
	}
}
