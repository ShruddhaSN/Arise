package com.arise.app.game

import com.arise.app.data.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Quote(val text: String, val by: String = "") {
    val line: String get() = if (by.isBlank()) "“$text”" else "“$text” · $by"
}

/**
 * Gentle quotes, never hustle-culture. Shown on waking, after a rest day, on level-ups and on the widget.
 * The order is a shuffle that only repeats once every quote has been shown; ♡ favourites come back more often.
 */
object Quotes {
    val all: List<Quote> = listOf(
        // Small steps
        Quote("A journey of a thousand miles begins with a single step.", "Lao Tzu"),
        Quote("It does not matter how slowly you go as long as you do not stop.", "Confucius"),
        Quote("Little by little, one travels far.", "J.R.R. Tolkien"),
        Quote("Do what you can, with what you have, where you are.", "Theodore Roosevelt"),
        Quote("The secret of getting ahead is getting started.", "Mark Twain"),
        Quote("Small deeds done are better than great deeds planned.", "Peter Marshall"),
        Quote("You don't have to see the whole staircase, just take the first step.", "Martin Luther King Jr."),
        Quote("Every action you take is a vote for the type of person you wish to become.", "James Clear"),
        Quote("You do not rise to the level of your goals. You fall to the level of your systems.", "James Clear"),
        Quote("Habits are the compound interest of self-improvement.", "James Clear"),
        Quote("Missing once is an accident. Missing twice is the start of a new habit. So just come back.", "after James Clear"),
        Quote("Start where you are. Use what you have. Do what you can.", "Arthur Ashe"),
        Quote("Well begun is half done.", "Aristotle"),
        Quote("We are what we repeatedly do.", "Will Durant, on Aristotle"),
        Quote("Motivation gets you going, but habit gets you there.", ""),
        Quote("Done is better than perfect.", ""),
        Quote("Progress, not perfection.", ""),
        Quote("Ten minutes is better than zero minutes.", ""),
        Quote("Slow progress is still progress.", ""),
        Quote("The best time to plant a tree was 20 years ago. The second best time is now.", "Proverb"),

        // Kind to yourself
        Quote("You are allowed to be both a masterpiece and a work in progress.", "Sophia Bush"),
        Quote("Rest is not quitting. Rest is part of the work.", ""),
        Quote("One bad day doesn't erase the progress. Today counts.", ""),
        Quote("Be patient with yourself. Nothing in nature blooms all year.", ""),
        Quote("You've been criticising yourself for years and it hasn't worked. Try approving of yourself and see what happens.", "Louise Hay"),
        Quote("Talk to yourself like you would to someone you love.", "Brené Brown"),
        Quote("Almost everything will work again if you unplug it for a few minutes, including you.", "Anne Lamott"),
        Quote("You don't have to be perfect to be amazing.", ""),
        Quote("Fall seven times, stand up eight.", "Japanese proverb"),
        Quote("Our greatest glory is not in never falling, but in rising every time we fall.", "Oliver Goldsmith"),
        Quote("It's okay to have a slow day. You're still moving.", ""),
        Quote("Healing is not linear.", ""),
        Quote("Comparison is the thief of joy.", "attributed to Theodore Roosevelt"),
        Quote("Your only competition is who you were yesterday.", ""),
        Quote("You are doing better than you think.", ""),

        // Stoic
        Quote("You have power over your mind, not outside events. Realise this, and you will find strength.", "Marcus Aurelius"),
        Quote("The happiness of your life depends upon the quality of your thoughts.", "Marcus Aurelius"),
        Quote("Waste no more time arguing what a good person should be. Be one.", "Marcus Aurelius"),
        Quote("Confine yourself to the present.", "Marcus Aurelius"),
        Quote("If it is not right, do not do it; if it is not true, do not say it.", "Marcus Aurelius"),
        Quote("We suffer more often in imagination than in reality.", "Seneca"),
        Quote("Luck is what happens when preparation meets opportunity.", "attributed to Seneca"),
        Quote("While we wait for life, life passes.", "Seneca"),
        Quote("It is not that we have a short time to live, but that we waste a lot of it.", "Seneca"),
        Quote("No man is free who is not master of himself.", "Epictetus"),
        Quote("First say to yourself what you would be; and then do what you have to do.", "Epictetus"),
        Quote("Some things are within our control, and some things are not.", "Epictetus"),
        Quote("Don't explain your philosophy. Embody it.", "Epictetus"),

        // Effort and growth
        Quote("Whether you think you can, or you think you can't, you're right.", "Henry Ford"),
        Quote("I have not failed. I've just found 10,000 ways that won't work.", "Thomas Edison"),
        Quote("It always seems impossible until it's done.", "Nelson Mandela"),
        Quote("The only way to do great work is to love what you do.", "Steve Jobs"),
        Quote("Believe you can and you're halfway there.", "Theodore Roosevelt"),
        Quote("Strength does not come from winning. Your struggles develop your strengths.", "attributed to Arnold Schwarzenegger"),
        Quote("The more that you read, the more things you will know.", "Dr. Seuss"),
        Quote("Live as if you were to die tomorrow. Learn as if you were to live forever.", "attributed to Mahatma Gandhi"),
        Quote("Education is the most powerful weapon which you can use to change the world.", "Nelson Mandela"),
        Quote("Dream is not that which you see while sleeping; it is something that does not let you sleep.", "A.P.J. Abdul Kalam"),
        Quote("If you want to shine like a sun, first burn like a sun.", "A.P.J. Abdul Kalam"),
        Quote("Don't take rest after your first victory, because if you fail in the second, more lips are waiting to say your first was luck.", "A.P.J. Abdul Kalam"),
        Quote("Arise, awake, and stop not till the goal is reached.", "Swami Vivekananda"),
        Quote("Take up one idea. Make that one idea your life.", "Swami Vivekananda"),
        Quote("You have a right to your actions, but never to the fruits of your actions.", "Bhagavad Gita"),
        Quote("Change is the law of the universe.", "Bhagavad Gita"),
        Quote("The mind is everything. What you think, you become.", "attributed to the Buddha"),
        Quote("Knowing is not enough; we must apply. Willing is not enough; we must do.", "Goethe"),
        Quote("Discipline is choosing between what you want now and what you want most.", ""),
        Quote("Courage doesn't always roar. Sometimes it's the quiet voice at the end of the day saying, I will try again tomorrow.", "Mary Anne Radmacher"),
        Quote("What we fear doing most is usually what we most need to do.", "Tim Ferriss"),
        Quote("A year from now you may wish you had started today.", "Karen Lamb"),
        Quote("Don't count the days. Make the days count.", "Muhammad Ali"),
        Quote("I hated every minute of training, but I said, don't quit. Suffer now and live the rest of your life as a champion.", "Muhammad Ali"),
        Quote("Hard work beats talent when talent doesn't work hard.", "Tim Notke"),
        Quote("The expert in anything was once a beginner.", "Helen Hayes"),
        Quote("You miss 100% of the shots you don't take.", "Wayne Gretzky"),
        Quote("Act as if what you do makes a difference. It does.", "William James"),
        Quote("Nothing will work unless you do.", "Maya Angelou"),
        Quote("We may encounter many defeats but we must not be defeated.", "Maya Angelou"),
        Quote("If you're going through hell, keep going.", "attributed to Winston Churchill"),
        Quote("Success is not final, failure is not fatal: it is the courage to continue that counts.", "attributed to Winston Churchill"),

        // Body and health
        Quote("Take care of your body. It's the only place you have to live.", "Jim Rohn"),
        Quote("Movement is a medicine for creating change in a person's physical, emotional and mental states.", "Carol Welch"),
        Quote("Sleep is the golden chain that ties health and our bodies together.", "Thomas Dekker"),
        Quote("A good laugh and a long sleep are the best cures in the doctor's book.", "Irish proverb"),
        Quote("To keep the body in good health is a duty, otherwise we shall not be able to keep our mind strong and clear.", "attributed to the Buddha"),
        Quote("The body achieves what the mind believes.", ""),
        Quote("Run when you can, walk if you have to, crawl if you must; just never give up.", "Dean Karnazes"),
        Quote("Of all the paths you take in life, make sure a few of them are dirt.", "John Muir"),
        Quote("An early-morning walk is a blessing for the whole day.", "Henry David Thoreau"),
        Quote("Methinks that the moment my legs begin to move, my thoughts begin to flow.", "Henry David Thoreau"),

        // Focus and time
        Quote("The future depends on what you do today.", "attributed to Mahatma Gandhi"),
        Quote("Lost time is never found again.", "Benjamin Franklin"),
        Quote("Well done is better than well said.", "Benjamin Franklin"),
        Quote("Either you run the day, or the day runs you.", "Jim Rohn"),
        Quote("Focus on being productive instead of busy.", "Tim Ferriss"),
        Quote("You can do anything, but not everything.", "David Allen"),
        Quote("Your mind is for having ideas, not holding them.", "David Allen"),
        Quote("Simplicity is the ultimate sophistication.", ""),
        Quote("The key is not to prioritise what's on your schedule, but to schedule your priorities.", "Stephen Covey"),
        Quote("How we spend our days is, of course, how we spend our lives.", "Annie Dillard"),

        // Korean proverbs
        Quote("시작이 반이다. Starting is half the work.", "Korean proverb"),
        Quote("천 리 길도 한 걸음부터. Even a thousand-li road starts with one step.", "Korean proverb"),
        Quote("티끌 모아 태산. Gather dust and it becomes a mountain.", "Korean proverb"),
        Quote("고생 끝에 낙이 온다. After hardship comes joy.", "Korean proverb"),
        Quote("하늘은 스스로 돕는 자를 돕는다. Heaven helps those who help themselves.", "Korean proverb"),
        Quote("낙숫물이 댓돌을 뚫는다. Dripping water wears through stone.", "Korean proverb"),
        Quote("쇠뿔도 단김에 빼라. Pull the ox's horn while it's hot: do it now.", "Korean proverb"),
        Quote("우물을 파도 한 우물을 파라. If you dig a well, dig just one.", "Korean proverb"),

        // Other proverbs
        Quote("Rome wasn't built in a day.", "Proverb"),
        Quote("Slow and steady wins the race.", "Aesop"),
        Quote("Drop by drop, the pot fills.", "Proverb"),
        Quote("The bamboo that bends is stronger than the oak that resists.", "Japanese proverb"),
        Quote("Vision without action is a daydream. Action without vision is a nightmare.", "Japanese proverb"),
        Quote("Even monkeys fall from trees.", "Japanese proverb"),
        Quote("Be not afraid of growing slowly, be afraid only of standing still.", "Chinese proverb"),
        Quote("Teachers open the door, but you must enter by yourself.", "Chinese proverb"),

        // The System
        Quote("The System does not need you to be perfect. It needs you to return.", "[SYSTEM]"),
        Quote("A Hunter is not made in a day. A Hunter is made in days.", "[SYSTEM]"),
        Quote("Level 1 is not weakness. It is the beginning.", "[SYSTEM]"),
        Quote("Rest recorded. Strength is being restored.", "[SYSTEM]"),
        Quote("Every quest completed is proof. Every quest skipped is data. Neither is failure.", "[SYSTEM]"),
        Quote("The gate is open. Walk through at your own pace.", "[SYSTEM]"),
        Quote("Today's goal: one more than nothing.", "[SYSTEM]"),
        Quote("Your stats only go up. Keep feeding them.", "[SYSTEM]"),
        Quote("The strongest Hunters were once the weakest. They just didn't stop.", "[SYSTEM]"),
        Quote("Arise.", "[SYSTEM]"),
    )

    /** Kind ones for the day after a rest day. */
    private val comeback: List<Int> = all.indices.filter { i ->
        val t = all[i].text
        listOf("bad day", "Fall", "fall", "Rest", "return", "slow", "Healing", "patient", "try again", "Missing once").any { it in t }
    }

    private fun order(seed: Long): List<Int> = all.indices.shuffled(java.util.Random(seed))

    /** Today's quote: a fixed shuffle cycle (no repeats inside a cycle); every 4th day a favourite, if you have any. */
    fun forDay(s: Settings, date: LocalDate): Pair<Int, Quote> {
        val n = ChronoUnit.DAYS.between(s.earliest, date).toInt().coerceAtLeast(0)
        val favs = s.favQuotes.filter { it in all.indices }.sorted()
        if (favs.isNotEmpty() && n % 4 == 3) {
            val i = favs[(n / 4) % favs.size]
            return i to all[i]
        }
        val cycle = n / all.size
        val i = order(s.earliest.toEpochDay() * 31L + cycle)[n % all.size]
        return i to all[i]
    }

    fun forComeback(date: LocalDate): Quote = all[comeback[Math.floorMod(date.toEpochDay().toInt(), comeback.size)]]

    /** A short line for level-ups. */
    fun forLevel(level: Int): Quote = all[Math.floorMod(level * 37, all.size)]
}
