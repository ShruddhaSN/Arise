package com.arise.app.game

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Card(val field: String, val title: String, val hook: String, val dig: String)

object Knowledge {
    fun forDay(origin: LocalDate, date: LocalDate): Card {
        val i = ChronoUnit.DAYS.between(origin, date).toInt()
        return cards[Math.floorMod(i, cards.size)]
    }

    val cards = listOf(
        Card("Body", "Insulin resistance", "Insulin is the key that lets sugar into your cells. With insulin resistance the lock gets stiff, so the body pumps out more insulin. Muscle is the biggest sugar sink in your body, which is why strength training helps so much.", "How does muscle take up glucose without insulin during exercise?"),
        Card("Science", "Why the sky is blue", "Sunlight scatters off air molecules, and short blue wavelengths scatter far more than red ones. This is called Rayleigh scattering. At sunset, light travels through more air and the blue gets scattered away.", "Why are sunsets red and orange?"),
        Card("History", "The printing press", "Around the 1440s, Johannes Gutenberg built a movable-type press in Mainz. Books became cheap, ideas spread fast, and it helped fuel the Reformation and the scientific revolution.", "How did the printing press change Europe?"),
        Card("Money", "Compound interest", "Your money earns returns, then those returns earn returns. Rule of 72: divide 72 by the yearly return to get the doubling time. At 12% a year, money roughly doubles every 6 years.", "What is an index fund and why do people recommend SIPs?"),
        Card("Space", "8 minutes of light", "Sunlight takes about 8 minutes 20 seconds to reach Earth. When you look at the Sun, you're seeing it as it was 8 minutes ago.", "How far is the nearest star after the Sun?"),
        Card("Mind", "The spacing effect", "Reviewing something over spaced-out sessions beats cramming it all at once. Your brain strengthens a memory each time it has to work to recall it.", "What is active recall and how do flashcard apps like Anki use it?"),
        Card("Biology", "Mitochondria were bacteria", "The powerhouses in your cells were probably free-living bacteria that got absorbed by another cell about 1.5–2 billion years ago. This is endosymbiotic theory. They even have their own DNA.", "Why is mitochondrial DNA inherited only from your mother?"),
        Card("Tech", "How the internet moves data", "Everything you send is chopped into small packets. Each one may take a different route and gets reassembled at the other end. TCP/IP is the set of rules that makes this work.", "What happens when you type a URL and press Enter?"),
        Card("Earth", "Why India has a monsoon", "In summer, land heats up faster than the ocean. Low pressure over the land pulls in moist winds from the sea, bringing the southwest monsoon from roughly June to September.", "What is El Niño and how does it affect the Indian monsoon?"),
        Card("Body", "Your body clock", "A master clock in your brain (the suprachiasmatic nucleus) runs your 24-hour rhythm. Morning light is its strongest reset signal. That's why your wake quest includes sunlight.", "How does melatonin work, and why do screens delay it?"),
        Card("Money", "Inflation", "When prices rise, every rupee buys less. If inflation is 6% and your savings earn 3%, your money is quietly losing value.", "Why do central banks raise interest rates to fight inflation?"),
        Card("History", "The Indus Valley Civilisation", "Cities like Harappa and Mohenjo-daro had grid-planned streets, standard bricks and covered drains. Their mature phase was roughly 2600–1900 BCE. Their script still hasn't been deciphered.", "Why did the Indus cities decline?"),
        Card("Science", "E = mc²", "A tiny amount of mass holds a huge amount of energy. The Sun shines by fusing hydrogen into helium, converting about 4 million tonnes of mass into energy every second.", "What is the difference between nuclear fission and fusion?"),
        Card("Biology", "DNA", "Your genetic code is written in just four letters: A, T, G and C. The human genome has about 3 billion of these pairs, and stretched out, the DNA in one cell is about 2 metres long.", "How does CRISPR edit genes?"),
        Card("Mind", "Dopamine is about wanting", "Dopamine drives anticipation and motivation more than pleasure itself. Endless scrolling keeps it firing on cheap rewards, which makes slower rewards feel boring.", "What is a dopamine detox, and what does the science actually say?"),
        Card("Tech", "How AI language models work", "Models like the one that helped build this app learn patterns from huge amounts of text. They then generate text one small piece (token) at a time, predicting what comes next.", "What is a neural network, explained simply?"),
        Card("Science", "Why ice floats", "Most things are denser as solids, but water expands when it freezes. Hydrogen bonds lock the molecules into an open lattice. If ice sank, lakes would freeze from the bottom up.", "What are hydrogen bonds?"),
        Card("Space", "Black holes", "A black hole's gravity is so strong that past the event horizon not even light can escape. In 2019 the Event Horizon Telescope took the first image of one.", "What is Hawking radiation?"),
        Card("Money", "Supply and demand", "When something is scarce and many people want it, its price goes up. When there's plenty and few buyers, the price falls. Most prices you see are this tug-of-war.", "Why do flight prices change so much?"),
        Card("History", "The Silk Road", "It wasn't one road but a network of trade routes linking China to the Mediterranean. Silk, spices and paper travelled along it, and so did religions, ideas and diseases.", "How did paper-making spread from China to the rest of the world?"),
        Card("Body", "VO₂ max", "VO₂ max is the most oxygen your body can use during hard effort. It's one of the strongest predictors of long-term health, and it improves with training, including the runs you're doing.", "How does Zone 2 training work?"),
        Card("Maths", "Bayes' theorem", "A rule for updating beliefs with new evidence. A positive test for a rare disease can still mean you probably don't have it, if the test has false positives.", "Explain Bayes' theorem with the medical test example."),
        Card("Art", "Why the Mona Lisa is famous", "It was respected but not world-famous until 1911, when it was stolen from the Louvre. The theft made headlines everywhere, and it came back as a global icon.", "Who stole the Mona Lisa and why?"),
        Card("Earth", "The Himalayas are still rising", "The Indian plate is still pushing into the Eurasian plate. The Himalayas formed from this collision and are still rising by a few millimetres each year.", "What is plate tectonics?"),
        Card("Body", "Your gut microbiome", "Trillions of microbes live in your gut. Fibre feeds the helpful ones, and they produce compounds linked to better blood-sugar control.", "Which foods are good for the gut microbiome?"),
        Card("Philosophy", "Stoicism", "Epictetus taught that some things are up to us (our choices and effort) and some aren't. Put your energy only into the first group. Very useful on bad days.", "Read a summary of Marcus Aurelius' Meditations."),
        Card("Tech", "Public-key encryption", "Your browser and a website exchange keys so that anyone can lock a message, but only the receiver can unlock it. That's what the padlock icon means.", "How does HTTPS work?"),
        Card("History", "Emperor Ashoka", "After the bloody Kalinga war, the Mauryan emperor Ashoka turned to Buddhism and non-violence. His Lion Capital at Sarnath is India's national emblem today.", "What did Ashoka's rock edicts say?"),
        Card("Science", "Why we have seasons", "It's not because of distance from the Sun. Earth's axis is tilted about 23.4°, so each hemisphere takes turns getting more direct sunlight.", "Why is it summer in Australia during India's winter?"),
        Card("Body", "Glycemic index", "Some foods spike blood sugar fast, others slowly. Eating protein, fibre and fat first, then carbs, slows the spike. That's why your quests say \"protein first\".", "What is the glycemic load, and how is it different from the glycemic index?"),
        Card("Language", "Sanskrit and English are cousins", "Both come from the same ancestor, Proto-Indo-European. Compare: matṛ → mother, pitṛ → father, nava → new.", "How did linguists reconstruct Proto-Indo-European?"),
        Card("Money", "Opportunity cost", "The true cost of anything is what you give up to get it. Two hours of scrolling costs two hours of something else.", "What is the sunk cost fallacy?"),
        Card("Space", "The Moon is leaving", "The Moon drifts about 3.8 cm farther from Earth every year. Scientists measure this by bouncing lasers off mirrors the Apollo astronauts left there.", "How do tides work?"),
        Card("Body", "Muscle talks to your body", "Working muscles release signalling molecules called myokines. They affect metabolism, inflammation and even the brain.", "What are myokines and what do they do?"),
        Card("History", "The Renaissance", "A burst of art, science and ideas in Europe from about the 14th to 17th century, starting in Italy. Leonardo, Michelangelo and Galileo all came out of it.", "Why did the Renaissance begin in Florence?"),
        Card("Mind", "The habit loop", "Habits run on cue → routine → reward. To build one, make the cue obvious and the reward immediate. That's exactly what this app's XP is for.", "Read about James Clear's Atomic Habits."),
        Card("Tech", "Moore's law", "For decades, the number of transistors on a chip roughly doubled every two years. That's why your phone is more powerful than the computers that sent people to the Moon.", "Is Moore's law ending?"),
        Card("Science", "Why onions make you cry", "Cutting an onion releases a gas (syn-propanethial-S-oxide) that irritates your eyes. Your eyes make tears to wash it out. Chilling the onion first helps.", "What other chemical defences do plants use?"),
        Card("Earth", "The Amazon makes its own rain", "Trees release huge amounts of water vapour through their leaves. Much of the Amazon's rain is recycled from the forest itself, so cutting trees can dry the whole region.", "What are \"flying rivers\"?"),
        Card("Maths", "Zero", "Indian mathematicians treated zero as a number in its own right. Brahmagupta wrote rules for calculating with it in 628 CE. Most of modern maths depends on it.", "How did Indian numerals reach Europe?"),
        Card("Body", "Antibiotic resistance", "Bacteria evolve. Each course of antibiotics kills the weak ones and can leave the resistant ones behind. That's why you finish a prescribed course and don't use antibiotics for viral colds.", "What are superbugs?"),
        Card("Space", "Chandrayaan-3", "On 23 August 2023, India became the first country to land near the Moon's south pole. Its rover, Pragyan, confirmed sulphur in the lunar soil.", "Why is the Moon's south pole interesting for future missions?"),
        Card("Music", "Why some notes sound good together", "Notes whose frequencies form simple ratios sound pleasant together. An octave is 2:1 and a perfect fifth is 3:2. Their sound waves line up often.", "What is a raga, and how is it different from a Western scale?"),
        Card("Body", "Cortisol", "Cortisol, the stress hormone, naturally peaks in the morning to wake you up. When it stays high all day from poor sleep or chronic stress, it worsens insulin resistance. Your sleep quest is hormone training.", "How does chronic stress affect PCOS?"),
    )
}
