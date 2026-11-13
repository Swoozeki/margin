package app.margin

val MODE_WORDS = listOf("Capture", "Question", "Critique", "Teach")

private val MODE_INSTRUCTIONS = listOf(
    "",
    "Question mode: if I ask you something about my story, answer briefly using my bible. If I am thinking out loud, react in a few words and ask me one good probing question. Do not give ideas unless I ask.",
    "Critique mode: give me honest, specific notes on what is working and what is not in what I just said. Do not flatter me.",
    "Teach mode: answer my craft or research question briefly, in a spoken style, with one tiny concrete example if it helps."
)

data class Msg(val mine: Boolean, val text: String)

fun buildSystemPrompt(mode: Int, ideas: List<Idea>, bible: List<Entry>, history: List<Msg>): String {
    val sb = StringBuilder()
    sb.append("You are a warm, sharp fiction-writing companion for an aspiring writer who prefers talking to typing. Talk like a real person in a relaxed conversation: contractions, natural rhythm, a brief reaction before your point. Keep every reply to one to three short sentences. No lists, no markdown, no emojis. Never write their story for them.\n\n")
    sb.append(MODE_INSTRUCTIONS[mode.coerceIn(1, 3)]).append("\n\n")
    var any = false
    for (cat in BIBLE_CATEGORIES) {
        val items = bible.filter { it.category == cat }
        if (items.isEmpty()) continue
        if (!any) {
            sb.append("Their story bible:\n")
            any = true
        }
        sb.append(cat).append(":\n")
        for (e in items.takeLast(15)) sb.append("- ").append(e.text).append("\n")
    }
    if (any) sb.append("\n")
    val recent = ideas.sortedByDescending { it.createdAt }.take(4)
    if (recent.isNotEmpty()) {
        sb.append("Their latest recordings:\n")
        for (i in recent) sb.append("- ").append(i.text.take(300)).append("\n")
        sb.append("\n")
    }
    if (history.isNotEmpty()) {
        sb.append("Conversation so far:\n")
        for (m in history) sb.append(if (m.mine) "Writer: " else "You: ").append(m.text.take(400)).append("\n")
    }
    return sb.toString()
}
