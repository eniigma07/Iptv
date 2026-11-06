# -*- coding: utf-8 -*-
"""Treść kursu Artykulacja. Uruchom: python3 tools/content/course.py <lex.json>
Sprawdza każde słowo w słowniku wymowy i zapisuje app/src/main/assets/course.json"""

SOUNDS = {
    # klucz = jednostka rozpoznawania (IPA)
    "θ": dict(name="TH bezdźwięczne", ex="think", how="Czubek języka lekko między zębami, wypuść powietrze bez głosu — jak syk przez szparę. Nie zamieniaj na „f”, „s” ani „t”."),
    "ð": dict(name="TH dźwięczne", ex="this", how="Ta sama pozycja co w „think” (język między zębami), ale z głosem — poczujesz drganie języka. Nie mów „d” ani „z”."),
    "ɪ": dict(name="krótkie ɪ", ex="ship", how="Krótko i luźno, coś między polskim „i” a „y”. Usta rozluźnione, bez uśmiechu."),
    "i": dict(name="długie i", ex="sheep", how="Długie, napięte „iii” z lekkim uśmiechem. Trzymaj dłużej niż polskie „i”."),
    "ɛ": dict(name="e otwarte", ex="bed", how="Jak polskie „e”, ale krótkie i czyste."),
    "æ": dict(name="szerokie æ", ex="plan", how="Szeroko otwórz usta jak do „a”, a język ustaw jak do „e”. Brzmi pośrodku między „a” i „e” — dłużej niż polskie samogłoski."),
    "ʌ": dict(name="krótkie ʌ", ex="budget", how="Krótkie, neutralne „a” z dołu gardła, usta tylko lekko otwarte — jak zdziwione „a?”."),
    "ɑ": dict(name="głębokie ɑ", ex="project", how="Głębokie, długie „a”, szeroko otwarte usta, język nisko i z tyłu. W amerykańskim „project”, „option”, „problem” to właśnie ten dźwięk, nie polskie „o”."),
    "ɔ": dict(name="otwarte ɔ", ex="call", how="Między „a” a „o”, usta lekko zaokrąglone. Wielu Amerykanów wymawia je jak ɑ — to też jest poprawne."),
    "ʊ": dict(name="krótkie ʊ", ex="full", how="Krótkie, luźne „u”, usta prawie nie zaokrąglone."),
    "u": dict(name="długie u", ex="fool", how="Długie, napięte „uuu” z zaokrąglonymi ustami."),
    "ɚ": dict(name="R-owa samogłoska", ex="manager", how="Amerykańskie „er”: język cofnięty i lekko zwinięty, bez polskiego „e” przed nim. „Manager” kończy się jednym miękkim dźwiękiem „ɚ”, nie „-er”."),
    "ɹ": dict(name="amerykańskie R", ex="right", how="Język NIE dotyka podniebienia i nie drga. Zwiń go lekko do tyłu, zaokrąglij usta. Żadnego polskiego „rrr”."),
    "w": dict(name="W angielskie", ex="we", how="Jak polskie „ł” — zaokrąglone usta, bez dotykania zębami wargi. Litera „w” w angielskim to NIE polskie „w”."),
    "v": dict(name="V", ex="very", how="Górne zęby na dolnej wardze, z głosem — jak polskie „w”."),
    "ŋ": dict(name="NG", ex="thinking", how="Tył języka przy podniebieniu, dźwięk idzie przez nos. Na końcu „-ing” nie dodawaj „k” ani „g”."),
    "eɪ": dict(name="dwugłoska eɪ", ex="plate", how="Zacznij od „e” i przesuń do „ɪ”: „ej”. Nie mów płaskiego polskiego „e”."),
    "oʊ": dict(name="dwugłoska oʊ", ex="go", how="Zacznij od „o” i domknij usta do „u”: „ołu”. Czyste polskie „o” brzmi obco."),
    "aɪ": dict(name="dwugłoska aɪ", ex="time", how="„aj” — płynne przejście od „a” do „ɪ”."),
    "aʊ": dict(name="dwugłoska aʊ", ex="now", how="„ał” — od „a” do zaokrąglonego „ʊ”."),
    "ɔɪ": dict(name="dwugłoska ɔɪ", ex="choice", how="„oj” — od otwartego „o” do „ɪ”."),
    "ʌ_schwa": dict(name="szwa ə", ex="about", how="Najkrótszy, najsłabszy dźwięk w angielskim — luźne „y/e”. Sylaby bez akcentu redukuj do szwy."),
    "d": dict(name="D", ex="planned", how="Język za górnymi zębami. Na końcu słowa zachowaj dźwięczność — nie zamieniaj na „t”."),
    "z": dict(name="Z", ex="his", how="Dźwięczne „z”. Na końcu słowa (his, plans, does) nie ubezdźwięczniaj do „s”."),
    "b": dict(name="B", ex="job", how="Na końcu słowa zachowaj dźwięczność — „job”, nie „jop”."),
    "ɡ": dict(name="G", ex="big", how="Na końcu słowa zachowaj dźwięczność — „big”, nie „bik”."),
    "v_final": dict(name="V na końcu", ex="have", how="„have”, „five” — dźwięczne v, nie „f”."),
    "t": dict(name="T", ex="team", how="Na początku sylaby z wyraźnym przydechem. Między samogłoskami Amerykanie mówią miękkie, szybkie „d” (water, data)."),
    "h": dict(name="H", ex="have", how="Lekki wydech, bez polskiego chrapliwego „ch”."),
    "l": dict(name="L", ex="all", how="Na końcu słowa „ciemne” L — tył języka uniesiony."),
    "dʒ": dict(name="DŻ", ex="manager", how="Jak polskie „dż”, ale z zaokrąglonymi ustami."),
    "tʃ": dict(name="CZ", ex="change", how="Jak polskie „cz”, z zaokrąglonymi ustami."),
    "ʃ": dict(name="SZ", ex="share", how="Jak polskie „sz”, z zaokrąglonymi ustami."),
    "ʒ": dict(name="Ż", ex="decision", how="Jak polskie „ż”."),
}

# Najczęstsze zamiany: "cel>usłyszane": wskazówka
SUBS = {
    "θ>t": "Brzmiało jak „t”. Nie dotykaj językiem dziąseł — wysuń go między zęby i dmuchnij.",
    "θ>s": "Brzmiało jak „s”. Język wyżej i do przodu, między zęby.",
    "θ>f": "Brzmiało jak „f”. Nie dotykaj wargi zębami — to język ma być między zębami.",
    "ð>d": "Brzmiało jak „d”. Język między zęby i z głosem, bez zwarcia.",
    "ð>z": "Brzmiało jak „z”. Język do przodu, między zęby.",
    "ð>v": "Brzmiało jak „v”. Wargi luźno — pracuje język między zębami.",
    "ɪ>i": "Za długo i za napięte — to krótkie, luźne ɪ (jak w „ship”).",
    "i>ɪ": "Za krótko — przeciągnij i uśmiechnij się lekko: „iii”.",
    "æ>ɛ": "Brzmiało jak polskie „e”. Otwórz szerzej usta, opuść żuchwę — æ.",
    "æ>ʌ": "Brzmiało jak „a”. Język bardziej do przodu, jak do „e”, przy szeroko otwartych ustach.",
    "æ>ɑ": "Brzmiało jak głębokie „a”. Przesuń język do przodu — æ jest jaśniejsze.",
    "ɛ>æ": "Za szeroko — to zwykłe, krótkie „e”.",
    "ʌ>ɑ": "Za głęboko i za długo — ʌ jest krótkie i neutralne.",
    "ʌ>æ": "Za jasno — ʌ jest krótkie, z tyłu, jak zdziwione „a?”.",
    "ɑ>ʌ": "Za krótko — otwórz szeroko usta i przeciągnij: „aa”.",
    "ɑ>oʊ": "Brzmiało jak „o”. W amerykańskim to długie, otwarte „a”: prAAject.",
    "ɑ>ɔ": "Prawie dobrze — otwórz usta szerzej, bez zaokrąglania.",
    "w>v": "Brzmiało jak polskie „w”. Nie dotykaj wargi zębami — zaokrąglij usta jak do „ł”.",
    "v>w": "Brzmiało jak „ł”. Górne zęby na dolną wargę: v.",
    "v>f": "Bez głosu — v musi być dźwięczne.",
    "ŋ>n": "Brzmiało jak „n”. Dźwięk z tyłu jamy ustnej, przez nos: ŋ.",
    "ɚ>ɛ": "Brzmiało jak polskie „e”. Zwiń lekko język do tyłu — amerykańskie ɚ.",
    "ɚ>ʌ": "Za mało R. Zwiń język do tyłu i zaokrąglij usta.",
    "ɹ>l": "Brzmiało jak „l”. Język nie dotyka podniebienia.",
    "eɪ>ɛ": "Płaskie „e” — dodaj ślizg na końcu: „ej”.",
    "oʊ>ɑ": "Zbyt otwarte. Zacznij od „o” i domknij usta do „u”.",
    "oʊ>ɔ": "Dodaj ślizg do „u” na końcu: „ołu”.",
    "d>t": "Brzmiało jak „t” — końcowe d ma być dźwięczne.",
    "z>s": "Brzmiało jak „s” — końcowe z ma być dźwięczne.",
    "b>p": "Brzmiało jak „p” — zachowaj dźwięczność.",
    "ɡ>k": "Brzmiało jak „k” — zachowaj dźwięczność.",
    "ʊ>u": "Za długo — ʊ jest krótkie i luźne.",
    "u>ʊ": "Za krótko — przeciągnij i zaokrąglij usta: „uuu”.",
}

UNITS = [
    dict(id="th1", title="TH bezdźwięczne", hook="think · three · thank", target="θ",
         lesson=[
             "Polacy najczęściej zamieniają „th” na „f”, „s” albo „t” — „fink”, „sank”, „tri”. Dla ucha native speakera to od razu słychać.",
             "Wysuń czubek języka lekko między zęby (nie za daleko) i wypuść powietrze. Żadnego głosu.",
             "Ćwicz powoli: najpierw sama głoska „θθθ”, potem słowo, potem zdanie.",
         ],
         words=["think", "thanks", "three", "third", "thousand", "growth", "month", "both", "through", "thorough", "Thursday", "method"],
         pairs=[["think", "sink"], ["thank", "tank"], ["three", "free"], ["thought", "taught"], ["path", "pass"], ["faith", "face"], ["mouth", "mouse"], ["thin", "tin"]],
         sentences=["I think we need three more weeks.", "Thanks for the thorough analysis.", "Growth was strong in the third quarter.", "Let's walk through the method together.", "We have both options on the table this month."]),
    dict(id="th2", title="TH dźwięczne", hook="this · the · they", target="ð",
         lesson=[
             "To samo ułożenie co w „think”, ale z głosem. Najczęstszy błąd: „dis”, „dey”, „ze”.",
             "„The”, „this”, „that”, „they”, „there” to jedne z najczęstszych słów — poprawienie ich zmienia całe brzmienie.",
             "Połóż palec na krtani: przy ð poczujesz drganie.",
         ],
         words=["this", "that", "they", "there", "the", "other", "whether", "together", "therefore", "although", "rather", "either"],
         pairs=[["they", "day"], ["then", "den"], ["those", "doze"], ["there", "dare"], ["though", "dough"], ["breathe", "breeze"], ["than", "Dan"]],
         sentences=["This is the other option.", "They agreed on that together.", "Therefore, we should rather wait.", "Whether they like it or not, the date is fixed.", "Is there anything else on the agenda?"]),
    dict(id="ih", title="Krótkie ɪ i długie i", hook="ship / sheep", target="ɪ",
         lesson=[
             "Polskie „i” leży pomiędzy dwiema angielskimi samogłoskami. Bez rozróżnienia „ship” i „sheep” brzmią tak samo.",
             "ɪ (ship, it, this) — krótkie i luźne, prawie jak polskie „y”.",
             "i (sheep, team, need) — długie i napięte, z lekkim uśmiechem.",
         ],
         words=["it", "this", "big", "risk", "business", "minute", "need", "team", "meeting", "deal", "key", "reach"],
         pairs=[["ship", "sheep"], ["fit", "feet"], ["fill", "feel"], ["sit", "seat"], ["still", "steal"], ["hit", "heat"], ["bit", "beat"], ["will", "wheel"]],
         sentences=["The team needs to meet this week.", "It is a big risk for the business.", "We will reach the key milestone in six weeks.", "Please fill in the sheet before the meeting.", "This deal is still in discussion."]),
    dict(id="ae", title="Szerokie æ", hook="plan · manager · have", target="æ",
         lesson=[
             "æ nie istnieje w polskim. Polacy mówią w jego miejsce „e” albo „a” — „men” zamiast „man”.",
             "Opuść żuchwę jak do „a”, a język ustaw jak do „e”. Dźwięk powinien być długi i jasny.",
             "Kluczowe słowa w biznesie: plan, manager, analysis, back, have, that, actually.",
         ],
         words=["plan", "manager", "back", "have", "that", "actually", "analysis", "factor", "challenge", "access", "chance", "task"],
         pairs=[["man", "men"], ["bad", "bed"], ["had", "head"], ["sat", "set"], ["pan", "pen"], ["bag", "beg"], ["track", "trek"], ["cap", "cup"]],
         sentences=["The manager has a plan.", "Let's get back to the analysis.", "Actually, that is a fantastic chance.", "We have to handle this challenge as a team.", "I'll ask the manager for access."]),
    dict(id="uh", title="ʌ i głębokie ɑ", hook="budget · project · problem", target="ʌ",
         lesson=[
             "ʌ (budget, much, run) jest krótkie i neutralne. ɑ (project, problem, option) — głębokie, długie „a”.",
             "W amerykańskim angielskim „project”, „problem”, „option”, „model”, „process” mają ɑ, a nie polskie „o”. To jedna z największych różnic w brzmieniu.",
             "Słuchaj wzorca i porównuj długość: ʌ krótko, ɑ długo.",
         ],
         words=["budget", "much", "run", "public", "result", "summary", "project", "problem", "option", "model", "process", "solve"],
         pairs=[["cut", "cat"], ["much", "match"], ["run", "ran"], ["hut", "hot"], ["luck", "lock"], ["duck", "dock"], ["but", "bought"]],
         sentences=["The budget is much higher than planned.", "We have a problem with the project.", "There are other options on the table.", "Let's run the process one more time.", "The result was published on Monday."]),
    dict(id="voice", title="Dźwięczne końcówki", hook="planned · his · job", target="d",
         lesson=[
             "W polskim na końcu słowa „d” brzmi jak „t” (sad → „sat”). W angielskim to zmienia znaczenie: bad / bat, plays / place.",
             "Utrzymaj głos do samego końca słowa. Samogłoska przed dźwięczną spółgłoską jest też nieco dłuższa.",
             "Uważaj na liczbę mnogą i czasowniki: plans, needs, does, has, is — końcowe „z”, nie „s”.",
         ],
         words=["need", "made", "planned", "his", "does", "has", "job", "big", "five", "move", "global", "plans"],
         pairs=[["bad", "bat"], ["bag", "back"], ["prize", "price"], ["eyes", "ice"], ["his", "hiss"], ["card", "cart"], ["plays", "place"], ["leave", "leaf"], ["prove", "proof"]],
         sentences=["He has made a good job of it.", "She needs five more days.", "We planned a big move.", "His team does a great job.", "The plans were approved and signed."]),
    dict(id="ng", title="Końcówka -ing", hook="thinking · meeting · going", target="ŋ",
         lesson=[
             "Polacy często mówią „meetink” albo „going-g”. W angielskim ŋ to jeden dźwięk — nosowy, bez „k” i bez „g” na końcu.",
             "Trzymaj tył języka przy podniebieniu i pozwól dźwiękowi wybrzmieć przez nos.",
             "Nie skracaj też do „n”: „meetin’” brzmi zbyt potocznie na spotkaniu zarządu.",
         ],
         words=["meeting", "thinking", "going", "working", "planning", "funding", "nothing", "something", "long", "bring", "strong", "among"],
         pairs=[["sing", "sink"], ["thing", "think"], ["wing", "wink"], ["rang", "rank"], ["ban", "bang"], ["thin", "thing"], ["win", "wing"]],
         sentences=["We are going to the meeting.", "I am thinking about something.", "The funding is still pending.", "Nothing is working as planned.", "Bring the planning documents."]),
    dict(id="r", title="Amerykańskie R", hook="right · report · manager", target="ɹ",
         lesson=[
             "Polskie „r” drga. Amerykańskie ɹ — nie: język nie dotyka podniebienia, jest lekko zwinięty do tyłu.",
             "Po samogłosce R zlewa się z nią w jeden dźwięk ɚ: manager, report, quarter, work, first.",
             "To jeden z najsilniejszych sygnałów amerykańskiego akcentu.",
         ],
         words=["right", "report", "really", "review", "priority", "strategy", "manager", "quarter", "work", "first", "customer", "number"],
         pairs=[["right", "light"], ["read", "lead"], ["road", "load"], ["grow", "glow"], ["pray", "play"], ["fry", "fly"]],
         sentences=["The report is really important.", "Our first priority is the customer.", "Let's review the numbers for the quarter.", "I am sure the strategy is right.", "We work hard for our partners."]),
    dict(id="wv", title="W i V", hook="we · very · well", target="w",
         lesson=[
             "Litera „w” w angielskim to dźwięk jak polskie „ł” — zaokrąglone usta, bez zębów. Polacy często czytają ją jak polskie „w”: „ve” zamiast „we”.",
             "V (very, value, review) — górne zęby na dolnej wardze, jak polskie „w”.",
             "Najtrudniejsze są połączenia: „very well”, „we will review”.",
         ],
         words=["we", "will", "well", "work", "week", "what", "very", "value", "review", "every", "over", "provide"],
         pairs=[["west", "vest"], ["wine", "vine"], ["wet", "vet"], ["while", "vile"], ["worse", "verse"], ["wail", "veil"]],
         sentences=["We will review it very well.", "What is the value for our customers?", "Every week we work over the weekend.", "We would like to provide a new view.", "Very well, we will move forward."]),
    dict(id="diph", title="Dwugłoski eɪ i oʊ", hook="go · plate · data", target="oʊ",
         lesson=[
             "W angielskim „o” w go, know, slow to dwugłoska: o + ʊ („ołu”). Czyste polskie „o” brzmi obco.",
             "Podobnie „a” w plate, change, data to eɪ: e + ɪ („ej”).",
             "Ślizg na końcu jest krótki, ale słyszalny.",
         ],
         words=["go", "know", "slow", "goal", "focus", "proposal", "only", "change", "data", "status", "update", "today"],
         pairs=[["coat", "caught"], ["boat", "bought"], ["so", "saw"], ["low", "law"], ["late", "let"], ["pain", "pen"], ["sale", "sell"], ["taste", "test"]],
         sentences=["Let's go over the proposal.", "I know the goal is to focus.", "Can you send me a status update today?", "Only the data will change.", "We need to make the same change again."]),
    dict(id="schwa", title="Szwa i słabe formy", hook="about · to · for", target="ʌ_schwa",
         lesson=[
             "W angielskim sylaby bez akcentu się redukują — do krótkiego, słabego ə. Polacy wymawiają każdą sylabę pełno, co brzmi jak czytanie.",
             "„to” → tə, „for” → fɚ, „and” → ən, „can” → kən, „of” → əv. W zdaniu akcentujesz słowa treści, a słowa funkcyjne „połykasz”.",
             "To klucz do płynności i rytmu native speakera.",
         ],
         words=["about", "agenda", "support", "today", "today", "problem", "season", "commitment", "decision", "official", "consider", "together"],
         pairs=[],
         sentences=["I want to talk about the agenda.", "We can support you with that.", "It is a matter of time and money.", "Let me give you an example.", "We are here to help and to listen."]),
    dict(id="ed", title="Końcówka -ed", hook="worked · planned · needed", target="d",
         lesson=[
             "-ed ma trzy wymowy: /t/ po bezdźwięcznych (worked, asked), /d/ po dźwięcznych (planned, agreed), /ɪd/ po t i d (needed, started).",
             "Nie dodawaj dodatkowej sylaby: „worked” to jedna sylaba, nie „work-ed”.",
             "Wyraźnie kończ słowo — to sygnał czasu przeszłego dla słuchacza.",
         ],
         words=["worked", "asked", "finished", "planned", "agreed", "delivered", "needed", "started", "decided", "reported", "aligned", "reviewed"],
         pairs=[],
         sentences=["We finished the review and asked for feedback.", "They agreed and signed the contract.", "We started early and delivered on time.", "She reported the issue and we fixed it.", "I decided we needed more time."]),
    dict(id="uu", title="Krótkie ʊ i długie u", hook="full / fool · good · move", target="ʊ",
         lesson=[
             "ʊ (good, look, full, should) — krótkie i luźne. u (move, soon, new, review) — długie, z zaokrąglonymi ustami.",
             "Polskie „u” leży pomiędzy — trzeba świadomie wybrać długość.",
         ],
         words=["good", "look", "full", "should", "could", "book", "move", "soon", "new", "review", "group", "choose"],
         pairs=[["full", "fool"], ["pull", "pool"], ["foot", "food"], ["should", "shoed"], ["could", "cooed"]],
         sentences=["We should look at the full picture.", "The new group will move soon.", "It could be a good solution.", "Let's choose a new approach.", "Please review the book."]),
]

STRESS = [
    ("development", "de·vel·op·ment", 1), ("analysis", "a·nal·y·sis", 1), ("strategy", "strat·e·gy", 0),
    ("process", "proc·ess", 0), ("company", "com·pa·ny", 0), ("schedule", "sched·ule", 0),
    ("deliverable", "de·liv·er·a·ble", 1), ("stakeholder", "stake·hold·er", 0), ("alignment", "a·lign·ment", 1),
    ("priority", "pri·or·i·ty", 1), ("opportunity", "op·por·tu·ni·ty", 2), ("implementation", "im·ple·men·ta·tion", 3),
    ("approval", "ap·prov·al", 1), ("management", "man·age·ment", 0), ("technology", "tech·nol·o·gy", 1),
    ("economic", "ec·o·nom·ic", 2), ("economy", "e·con·o·my", 1), ("develop", "de·vel·op", 1),
    ("percent", "per·cent", 1), ("success", "suc·cess", 1), ("successful", "suc·cess·ful", 1),
    ("presentation", "pres·en·ta·tion", 2), ("communication", "com·mu·ni·ca·tion", 3), ("infrastructure", "in·fra·struc·ture", 0),
    ("procurement", "pro·cure·ment", 1), ("committee", "com·mit·tee", 1), ("contract", "con·tract", 0),
    ("budget", "budg·et", 0), ("revenue", "rev·e·nue", 0), ("category", "cat·e·go·ry", 0),
    ("analyze", "an·a·lyze", 0), ("photographer", "pho·tog·ra·pher", 1), ("photograph", "pho·to·graph", 0),
    ("governance", "gov·er·nance", 0), ("customer", "cus·tom·er", 0), ("agenda", "a·gen·da", 1),
    ("efficient", "ef·fi·cient", 1), ("objective", "ob·jec·tive", 1), ("executive", "ex·ec·u·tive", 1),
    ("organization", "or·gan·i·za·tion", 3), ("negotiation", "ne·go·ti·a·tion", 3), ("mitigation", "mit·i·ga·tion", 2),
]

PHRASES = [
    ("Otwieranie spotkania", [
        "Thanks everyone for joining today.",
        "Let's get started, we have a lot to cover.",
        "The purpose of this meeting is to agree on the next steps.",
        "Before we begin, let me quickly walk you through the agenda.",
        "We have one hour, so let's keep it focused.",
        "I'd like to start with a quick update on where we are.",
        "Does anyone have anything to add to the agenda?",
        "Let's make sure we leave with clear decisions.",
    ]),
    ("Status i postęp", [
        "Overall, the project is on track.",
        "We are slightly behind schedule, but we have a recovery plan.",
        "The main deliverables for this sprint are complete.",
        "We have made good progress since our last meeting.",
        "The key milestone is planned for the end of the month.",
        "Two items are still open, and we are working on them.",
        "The budget is under control, and we expect no overrun.",
        "Let me highlight three points from the status report.",
    ]),
    ("Komitet sterujący", [
        "We need a decision from this committee today.",
        "I would like to ask for your approval on the revised scope.",
        "There are two options, and we recommend the second one.",
        "The business case remains strong.",
        "We have aligned with all the key stakeholders.",
        "The main dependency is on the vendor side.",
        "This change will have an impact on the timeline.",
        "We propose to move the go live date by two weeks.",
    ]),
    ("Ryzyka i eskalacje", [
        "I want to flag a risk early.",
        "This issue needs to be escalated.",
        "If we don't act now, we will miss the deadline.",
        "We have identified the root cause.",
        "Our mitigation plan is already in place.",
        "I need your support to unblock this.",
        "The risk is high, but it is manageable.",
        "Let's agree on an owner and a due date.",
    ]),
    ("Negocjacje", [
        "I understand your position, and I appreciate your flexibility.",
        "What would it take to close this today?",
        "We are open to discussing the terms.",
        "That is a fair point, but our costs have increased.",
        "Could you help me understand the reasoning behind this?",
        "If we commit to a longer contract, can you improve the price?",
        "Let's find a solution that works for both sides.",
        "I'm afraid that is outside our budget.",
    ]),
    ("Decyzje i rekomendacje", [
        "Based on the data, my recommendation is clear.",
        "We have considered all the alternatives.",
        "The trade off is between speed and quality.",
        "I suggest we take a phased approach.",
        "Let's go with option two.",
        "We need to prioritize what really matters.",
        "This is the right thing to do for the business.",
        "I am confident this is the best way forward.",
    ]),
    ("Trudne rozmowy i feedback", [
        "I'd like to share some feedback with you.",
        "I appreciate your effort, and I think we can do better.",
        "Let's focus on the facts, not on opinions.",
        "Help me understand what happened.",
        "I hear your concern, and I take it seriously.",
        "What support do you need from me?",
        "I expected a different outcome.",
        "Let's agree on what success looks like.",
    ]),
    ("Prezentowanie", [
        "Let me walk you through the key findings.",
        "As you can see on this slide, revenue is growing.",
        "There are three things I want you to remember.",
        "To put it simply, we are ahead of our targets.",
        "This brings me to my next point.",
        "Let me pause here for questions.",
        "In summary, the results speak for themselves.",
        "Thank you for your attention.",
    ]),
    ("Small talk z klientem", [
        "How was your trip?",
        "It's great to finally meet you in person.",
        "How are things on your side?",
        "Have you been busy lately?",
        "We really enjoy working with your team.",
        "Are you planning any time off this summer?",
        "Let me know if there is anything I can do for you.",
        "Looking forward to seeing you again soon.",
    ]),
    ("Podsumowanie i kolejne kroki", [
        "Let me summarize what we have agreed.",
        "I will send the minutes after the meeting.",
        "The next steps are clear.",
        "Who will take the lead on this?",
        "Let's reconvene next week.",
        "Please send me your comments by Friday.",
        "Thank you all, this was a very productive session.",
        "I will follow up with the team.",
    ]),
]

PLACEMENT = [
    "I think this is the right strategy for the third quarter.",
    "We need to review the budget with the whole team.",
    "The manager asked for a clear plan and a realistic timeline.",
    "Let's focus on the things we can actually deliver this month.",
    "Our stakeholders want a status update on Thursday.",
    "Both options have risks, so we should agree on the priorities.",
    "I'm going to share the results and the next steps.",
    "Very well, let's move forward with the proposal.",
]

VOICES = [
    ("af_heart", "Heart", "Ciepły, naturalny, bardzo wyraźny", True),
    ("af_bella", "Bella", "Aksamitny i ekspresyjny", True),
    ("af_nicole", "Nicole", "Szeptany, intymny, bardzo miękki", True),
    ("af_aoede", "Aoede", "Spokojny, melodyjny", False),
    ("af_kore", "Kore", "Pewny siebie, profesjonalny", False),
    ("af_sarah", "Sarah", "Dojrzały, rzeczowy", False),
    ("af_nova", "Nova", "Świeży, pogodny", False),
    ("af_sky", "Sky", "Lekki, delikatny", False),
    ("af_alloy", "Alloy", "Neutralny, biznesowy", False),
    ("af_jessica", "Jessica", "Energiczny", False),
    ("af_river", "River", "Łagodny, płynący", False),
    ("am_michael", "Michael", "Ciepły baryton", True),
    ("am_fenrir", "Fenrir", "Głęboki, męski", True),
    ("am_onyx", "Onyx", "Niski, aksamitny", False),
    ("am_puck", "Puck", "Swobodny, uśmiechnięty", False),
    ("am_echo", "Echo", "Spokojny, wyważony", False),
    ("am_eric", "Eric", "Rzeczowy, biznesowy", False),
    ("am_liam", "Liam", "Młody, przyjazny", False),
    ("am_adam", "Adam", "Mocny, wyraźny", False),
]

VOWELS = set("AIOWYaæɑɔəɛɜɪʊʌiuᵊᵻ")

if __name__ == "__main__":
    import json, re, sys
    lex = json.load(open(sys.argv[1]))

    def look0(w):
        for k in (w, w.lower(), w.capitalize()):
            if k in lex:
                return lex[k]
        return None

    def look(w):
        r = look0(w)
        if r is not None:
            return r
        w = w.lower()
        def plural(b):
            last = b.rstrip("ˈˌ")[-1]
            if last in "szʃʒʧʤ": return b + "ᵻz"
            if last in "ptkfθ": return b + "s"
            return b + "z"
        def past(b):
            last = b.rstrip("ˈˌ")[-1]
            if last in "tdɾ": return b + "ᵻd"
            if last in "pkfθsʃʧ": return b + "t"
            return b + "d"
        if w.endswith("'s"):
            b = look0(w[:-2]); return plural(b) if b else None
        if w.endswith("ies") and look0(w[:-3] + "y"): return plural(look0(w[:-3] + "y"))
        if w.endswith("es") and look0(w[:-2]): return plural(look0(w[:-2]))
        if w.endswith("s") and look0(w[:-1]): return plural(look0(w[:-1]))
        if w.endswith("ied") and look0(w[:-3] + "y"): return past(look0(w[:-3] + "y"))
        if w.endswith("ed"):
            for b in (w[:-2], w[:-1], w[:-3] if len(w) > 4 and w[-3] == w[-4] else None):
                if b and look0(b): return past(look0(b))
        if w.endswith("ing"):
            for b in (w[:-3], w[:-3] + "e", w[:-4] if len(w) > 5 and w[-4] == w[-5] else None):
                if b and look0(b): return look0(b) + "ɪŋ"
        if w.endswith("ly") and look0(w[:-2]): return look0(w[:-2]) + "li"
        return None

    missing = set()

    def check_text(t):
        for w in re.findall(r"[A-Za-z']+", t):
            if look(w) is None and look(w.strip("'")) is None:
                missing.add(w)

    for u in UNITS:
        for w in u["words"]:
            check_text(w)
        for a, b in u["pairs"]:
            check_text(a); check_text(b)
        for s in u["sentences"]:
            check_text(s)
    for _, items in PHRASES:
        for s in items:
            check_text(s)
    for s in PLACEMENT:
        check_text(s)
    bad_stress = []
    for w, syl, st in STRESS:
        p = look(w)
        if p is None:
            missing.add(w); continue
        nv = 0; stress_at = None
        i = 0
        while i < len(p):
            c = p[i]
            if c == "ˈ":
                stress_at = nv
            elif c in VOWELS:
                nv += 1
            i += 1
        n = len(syl.split("·"))
        if nv != n or stress_at != st:
            bad_stress.append((w, p, syl, st, nv, stress_at))
    print("BRAK W SŁOWNIKU:", sorted(missing))
    print("AKCENT NIEZGODNY:")
    for b in bad_stress:
        print("  ", b)
    out = dict(
        sounds=SOUNDS, subs=SUBS, units=UNITS,
        stress=[dict(word=w, syl=s.split("·"), stress=i) for w, s, i in STRESS],
        phrases=[dict(cat=c, items=i) for c, i in PHRASES],
        placement=PLACEMENT,
        voices=[dict(id=a, name=b, desc=c, top=d) for a, b, c, d in VOICES],
    )
    json.dump(out, open("app/src/main/assets/course.json", "w"), ensure_ascii=False, indent=1)
    print("zapisano course.json")
