package abc.parser;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import abc.sound.*;
import abc.sound.Header.HeaderBuilder;
import lib6005.parser.*;

/**
 * An immutable data type representing a musical composition of:
 *   the treble clef staff
 *   measures
 *   all octaves
 *   all rests
 *   all notes of any duration
 *   accidentals
 *   chords
 *   duplets, triplets, and quadruplets
 *   repeats and endings
 */
public class MusicParser {
    
    //Grammar for the header parser
    enum HeaderGrammar{ROOT, TITLE, OPTION, KEY, 
        VOICE, VOICENAME, AUTHOR, LENGTH, METER, SPECIALMETER, TEMPO, NAME, FRACTION, INTEGER, WHITESPACE};
    //grammar for the body parser
    enum ABCGrammar{ROOT, MAJORSECTION, SEQUENCE, BLOCK, REPEAT, START, END1, END2, MEASURE, ELEMENT,
                    REST, NOTE, CHORD, TUPLET, DUPLET, TRIPLET, QUADRUPLET, PITCH, DURATION, ACCIDENTAL, WHITESPACE};
    
    //Array that converts major keys to an int with index 0 being 7 flats and index 14 = 7 sharps
    private final static List<String> MAJORKEYS = Arrays.asList(
            "Cb", "Gb", "Db", "Ab", "Eb", "Bb", "F", "C", "G", "D", "A", "E", "B", "F#", "C#");
    //Same array as above for minor keys
    private final static List<String> MINORKEYS = Arrays.asList(
            "Abm", "Ebm", "Bbm", "Fm", "Cm", "Gm", "Dm", "Am", "Em", "Bm", "F#", "C#m", "G#m", "D#m", "A#m");
    
    /**
     * Parse Music.git 
     * @param input expression to parse, as defined in the PS3 handout.
     * @return MusicPiece AST for the input
     * @throws IllegalArgumentException if the expression is invalid 
     */
    public static MusicPiece parse(File inputMusic){
        try {
            //Convert file into string
            String input = fileToString(inputMusic);
            
            //Cut input string into header part
            String header = getHeader(input);
            
            //Parse the header of abc file into a header class
            Parser<HeaderGrammar> headerParser = GrammarCompiler.compile(
                    new File("src/abc/parser/HeaderGrammar.g"), HeaderGrammar.ROOT);
            ParseTree<HeaderGrammar> headerTree = headerParser.parse(header);
            Header musicHeader = buildHeader(headerTree, header);
            
            //Get the voices from the header
            List<String> voices = musicHeader.getVoices();
                //if there are no voices, there is only one line
            if (voices.size() == 0)
                voices.add("");
            
            //Parse the body into voices
            Parser<ABCGrammar> bodyParser = GrammarCompiler.compile(new File("src/abc/parser/Abc.g"), ABCGrammar.ROOT);
            
            List<MusicSequence> voiceSequences = new ArrayList<MusicSequence>();
            for(String voiceName: voices) {
                String voice = getVoice(input, voiceName);
                ParseTree<ABCGrammar> voiceTree = bodyParser.parse(voice);
                MusicSequence voiceSequence = buildVoice(voiceTree, musicHeader);
                voiceSequences.add(voiceSequence);
            }
            
            //Combine the two parts
            return new MusicPiece(musicHeader, voiceSequences);
               
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse!");
        } catch (UnableToParseException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Cannot parse!");
        }
    }
    
    
    
    /**
     * Helper method, returns the part of the String corresponding to the header of the piece
     * @param music a piece of music in valid abc notation
     * @return String the header of the music piece
     */
    static String getHeader(String music) {
        Pattern headerPattern = Pattern.compile("(?m)(^.+$\\s+)+K:.+$");
        Matcher matcher = headerPattern.matcher(music);
        if(matcher.find()) {
            String header = matcher.group(0);
            return header;  
        } else
            throw new RuntimeException("Could not split header");
    }
    
    /**
     * Helper method, turns a file into a string
     * @param file
     * @return String file as a String
     * @throws FileNotFoundException 
     */
    static String fileToString(File file) {
        Scanner scanner;
        String content = "";
        try {
            //scan the whole file as one String
            scanner = new Scanner(file);
            scanner.useDelimiter("\\Z");
            content = scanner.next();
            scanner.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("File not found");
        }
        
        return content;
    }
    
    
    /**
     * Helper method, concatenates all the music segments of voiceName into one String
     * @param music a piece of music in valid abc notation
     * @param voiceName , empty iff there is only one voice
     * @return List<String> a list with the voiceName segments as a single String
     */
    static String getVoice(String music, String voiceName) {
        String body = music.replaceFirst("(?m)(^.+$\\s+)+K:.+$", ""); //remove the header
        
        Scanner scanner = new Scanner(body);
        
        //create regex for detecting whether the following lines are of the voice
        String thisVoiceTag = ("V:\\s*" + voiceName + "\\s*" );
        String otherVoiceTag = ("V:\\s*.+");
        
        //will start out true if there is only one voice, false otherwise
        boolean isVoice = voiceName.isEmpty();
        String voice = "";
        //iterate through all lines of the body, adding the voice lines into one line
        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            //if the line is empty or a comment, skip it
            while(line.isEmpty() || line.startsWith("%"))
                line = scanner.nextLine();
            
            if (line.matches(thisVoiceTag)) { 
                //lines after "V: voiceName" should be read as this voice
                isVoice = true; 
            } else if (line.matches(otherVoiceTag)) {
                //lines after "V: otherVoice" should not be read as this voice
                isVoice = false; 
            } else if (isVoice) {
                voice = voice.concat(line);
            } else {
                continue;
            }  
        }
        scanner.close();
        return voice;
    }
    
    /**
     * Creates a header from the header tree
     * @param headerTree parsed tree for the header
     * @param fullHeader the full text of the header
     * @return a Header with the information given in this abc file
     */
    private static Header buildHeader(ParseTree<HeaderGrammar> headerTree, String fullHeader) {
        
        HeaderBuilder hBuilder = new HeaderBuilder();
        
        hBuilder.setFullHeader(fullHeader);
        
        for(ParseTree<HeaderGrammar> tree : headerTree.children()) {
            switch(tree.getName()) {
            
            case TITLE:
                //Get name from title tree
                String titleName = tree.childrenByName(HeaderGrammar.NAME).get(0).getContents();
                hBuilder.setTitle(titleName);
                break;
            case OPTION:
                //Nested switch statement to parse different options
                //author, meter, length, tempo, and voice;
                ParseTree<HeaderGrammar> optionTree = tree.children().get(0);
                switch(optionTree.getName()) {
                
                case AUTHOR:
                    //Get name from author tree
                    String composer = optionTree.childrenByName(HeaderGrammar.NAME).get(0).getContents();
                    hBuilder.setComposer(composer);
                    break;
                case METER:
                    int meterNum, meterDen;
                    
                    //account for special meter
                    if(optionTree.childrenByName(HeaderGrammar.FRACTION).size() == 0) {
                        ParseTree<HeaderGrammar> specialTree = optionTree.childrenByName(HeaderGrammar.SPECIALMETER).get(0);
                        if(specialTree.getContents().equals("C"))
                            meterNum = meterDen = 4;
                        else
                            meterNum = meterDen = 2;
                    } else {
                        // Get meter from tree by getting the fraction,
                        ParseTree<HeaderGrammar> fractionTree = optionTree.childrenByName(HeaderGrammar.FRACTION).get(0);

                        // then finding the numerator and denominator separately
                        String meterNumString = fractionTree.childrenByName(HeaderGrammar.INTEGER).get(0).getContents();
                        String meterDenString = fractionTree.childrenByName(HeaderGrammar.INTEGER).get(1).getContents();
                        meterNum = Integer.parseInt(meterNumString);
                        meterDen = Integer.parseInt(meterDenString);
                    }
                    
                    hBuilder.setMeter(meterNum, meterDen);
                    break;
                case LENGTH:
                    //Get length double from length tree by getting the fraction, 
                    ParseTree<HeaderGrammar> fractionTree = optionTree.childrenByName(HeaderGrammar.FRACTION).get(0);
                    
                    //then finding the numerator and denominator separately
                    String lengthNumString = fractionTree.childrenByName(HeaderGrammar.INTEGER).get(0).getContents();
                    String lengthDenString = fractionTree.childrenByName(HeaderGrammar.INTEGER).get(1).getContents();
                    int lengthNum = Integer.parseInt(lengthNumString);
                    int lengthDen = Integer.parseInt(lengthDenString);
                    
                    double length = lengthNum / (double)lengthDen;
                    hBuilder.setLength(length);
                    break;
                case TEMPO:
                    //get default beat length from tempo tree
                    ParseTree<HeaderGrammar> fraction = optionTree.childrenByName(HeaderGrammar.FRACTION).get(0);
                    String beatNumString = fraction.childrenByName(HeaderGrammar.INTEGER).get(0).getContents();
                    String beatDenString = fraction.childrenByName(HeaderGrammar.INTEGER).get(1).getContents();
                    int beatNum = Integer.parseInt(beatNumString);
                    int beatDen = Integer.parseInt(beatDenString);
                    hBuilder.setBeatLength(beatNum / (double)beatDen);
                    
                    //Get tempo int from tempo tree
                    String tempoString = optionTree.childrenByName(HeaderGrammar.INTEGER).get(0).getContents();
                    int tempo = Integer.parseInt(tempoString);
                    hBuilder.setTempo(tempo);
                    break;
                case VOICE:
                    //Get voice name from tree
                    String voiceName = optionTree.childrenByName(HeaderGrammar.VOICENAME).get(0).getContents();
                    hBuilder.addVoice(voiceName);
                    break;
                default:
                    throw new RuntimeException("Should never get here");
                }
                break;
            case KEY:
                String key = tree.getContents();
                int keyInt, keyIndex;
                
                //Convert key into an int based on how many sharps or flats it has
                if(MAJORKEYS.contains(key))
                    keyIndex = MAJORKEYS.indexOf(key);
                else if (MINORKEYS.contains(key)) 
                    keyIndex = MINORKEYS.indexOf(key);
                else
                    throw new RuntimeException("Key is unknown");
                
                //convert 0 - 14 range to -7 - 7 range where -7 is 7 flats and 7 is 7 sharps
                keyInt = keyIndex - 7;
                hBuilder.setKey(keyInt);
                break;
            case WHITESPACE:
                break;
            default:
                throw new RuntimeException("Should never get here");
            }
        }
        
        return hBuilder.createHeader();
    }
    
    /**
     * Determines the music AST from a tree
     * @param voiceTree ParseTree<ABCGrammar> derived from the parse method
     * @return MusicPiece AST for the ParseTree
     */
    private static MusicSequence buildVoice(ParseTree<ABCGrammar> tree, Header header) {
        
            switch(tree.getName()) { 
            
            
            case ROOT:
                //composed of major sections, return all sections joined together in order
                List<ParseTree<ABCGrammar>> majorSections = tree.childrenByName(ABCGrammar.MAJORSECTION);
                MusicSequence voice = buildVoice(majorSections.get(0), header);
                for (int i = 1; i < majorSections.size(); i++) {
                    voice = MusicSequence.join(voice, buildVoice(majorSections.get(i), header));
                }
                return voice;
                
                
            case MAJORSECTION:
                //composed of a repeat in the beginning and/or a sequence, return both parts joined together in order
                List<ParseTree<ABCGrammar>> repeats = tree.childrenByName(ABCGrammar.REPEAT);
                List<ParseTree<ABCGrammar>> sequences = tree.childrenByName(ABCGrammar.SEQUENCE);
                if (repeats.size() == 0) {
                    //if there is no repeat there must be a sequence
                    return buildVoice(sequences.get(0), header);
                } else if (sequences.size() == 0) {
                    //if there is no sequence there must be a repeat
                    return buildVoice(repeats.get(0), header);
                } else {
                    //repeat and sequence
                    return MusicSequence.join(buildVoice(repeats.get(0), header), buildVoice(sequences.get(0), header));
                }
                        
                
            case SEQUENCE:
                //composed of blocks, return all blocks joined together in order
                List<ParseTree<ABCGrammar>> blocks = tree.childrenByName(ABCGrammar.BLOCK);
                MusicSequence concatBlocks = buildVoice(blocks.get(0), header);
                for (int i = 1; i < blocks.size(); i++) {
                    concatBlocks = MusicSequence.join(concatBlocks, buildVoice(blocks.get(i), header));
                }
                return concatBlocks;
                
            case BLOCK:
                //can be a repeat or a measure
                for (ParseTree<ABCGrammar> block : tree.children()) {
                    if (!block.getName().equals(ABCGrammar.WHITESPACE)) {//exclude whitespace children
                        return buildVoice(block, header);
                    }
                }
                throw new RuntimeException("Block did not have expected children");
                
            case REPEAT:
                //has a start, and possibly two different endings
                //concatenate as start end1? start end2?
                for (ParseTree<ABCGrammar> start : tree.children()) {
                    if (start.getName().equals(ABCGrammar.START)) {
                        MusicSequence repeat = buildVoice(start, header);
                        for (ParseTree<ABCGrammar> end1 : tree.children()) {
                            if (end1.getName().equals(ABCGrammar.END1)) {
                                repeat = MusicSequence.join(repeat, buildVoice(end1, header));
                            }
                        }
                        repeat = MusicSequence.join(repeat, buildVoice(start, header));
                        for (ParseTree<ABCGrammar> end2 : tree.children()) {
                            if (end2.getName().equals(ABCGrammar.END2)) {
                                repeat = MusicSequence.join(repeat, buildVoice(end2, header));
                            }
                        }
                        return repeat;
                    }
                }
                throw new RuntimeException("Repeat did not have expected children");
                
            case START:
                //has a sequence
                return buildVoice(tree.childrenByName(ABCGrammar.SEQUENCE).get(0), header);
                
            case END1:
                //has a sequence
                return buildVoice(tree.childrenByName(ABCGrammar.SEQUENCE).get(0), header);
                
            case END2:
                //has a block
                return buildVoice(tree.childrenByName(ABCGrammar.BLOCK).get(0), header);
                
            case MEASURE:
                //has one or more elements
                List<NoteElement> notes = new ArrayList<>();
                for (ParseTree<ABCGrammar> child : tree.childrenByName(ABCGrammar.ELEMENT)) {
                    Map<String, String> accidentals = new HashMap<>();
                    notes.add(buildElement(child, header, accidentals));
                    
                }
                MusicSequence measure = MusicSequence.measure(notes);
                return measure;
            default:
                throw new RuntimeException("Should not reach default clause");
            }
            
            
        
        
        
    }
    
    /**
     * Helper method, returns note elements from ParseTrees corresponding to NoteElements, taking into 
     * account the length, key, and a map of accidentals previously found in the measure
     * @param elementTree ParseTree<ABCGrammar> corresponding to a note element
     * @param header Header 
     * @param accidentalMap Map<String pitch (Ex: a'') , String accidental (Ex: "^")>
     * @return NoteElement
     */
    private static NoteElement buildElement(ParseTree<ABCGrammar> tree, Header header, Map<String, String> accidentalMap) {
        
       
            switch(tree.getName()) {
            
            case ELEMENT:
                //can be a rest, note, chord, or tuplet 
                for(ParseTree<ABCGrammar> noteElement : tree.children()) {
                    if (!noteElement.getName().equals(ABCGrammar.WHITESPACE)) { //exclude whitespace children
                        return buildElement(noteElement, header, accidentalMap);
                    }
                }
                
            case REST:
                //can have duration, else set default duration
                return NoteElement.rest(parseDuration(tree, header));
                
                
            case NOTE:
                //must have pitch, can have accidental, duration
                double noteDuration = parseDuration(tree, header);
                String notePitch = tree.childrenByName(ABCGrammar.PITCH).get(0).getContents();
                char[] pitchChars = notePitch.toCharArray();
                int octave = 0; //number of octaves higher or lower than middle C
                if (Character.isLowerCase(pitchChars[0])) {
                    octave = pitchChars.length; // 1 + number of ['] characters
                } else {
                    octave = 1 - pitchChars.length; //number of [,] characters
                }
                char pitchLetter = Character.toUpperCase(pitchChars[0]); //pitch letter to be used by constructor
                String noteAccidental = "="; //normal, no accidental
                if (tree.childrenByName(ABCGrammar.ACCIDENTAL).size() == 1) { //if the note has an accidental
                    noteAccidental = tree.childrenByName(ABCGrammar.ACCIDENTAL).get(0).getContents();
                    accidentalMap.put(notePitch, noteAccidental); //this will notify future notes in the measure of the accidental
                } else if (accidentalMap.containsKey(notePitch)) {
                    //if a previous note in the measure with this pitch had an accidental
                    noteAccidental = accidentalMap.get(notePitch);
                } else { //modify accidental based on key
                    int key = header.getKey();
                    char[] sharpKeys = {'F', 'C', 'G', 'D', 'A', 'E', 'B'};
                    char[] flatKeys = {'B', 'E', 'A', 'D', 'G', 'C', 'F'};
                    if (key > 0) {
                        for (int i = 0; i < key; i++) {
                            if (pitchLetter == sharpKeys[i]) {
                                noteAccidental = "^";
                                break;
                            }
                        }  
                    } else if (key < 0) {
                        key = -1 * key;
                            for (int i = 0; i < key; i++) {
                                if (pitchLetter == flatKeys[i]) {
                                    noteAccidental = "_";
                                }
                            }
                    }
                }
                return NoteElement.note(octave, noteAccidental, pitchLetter, noteDuration);
                
                
            case CHORD:
                //has one or more notes
                List<NoteElement> chordNotes = new ArrayList<>();
                for (ParseTree<ABCGrammar> note : tree.childrenByName(ABCGrammar.NOTE)) {
                    chordNotes.add(buildElement(note, header, accidentalMap));
                }
                return NoteElement.chord(chordNotes);
                
            case TUPLET:
                List<NoteElement> tupletNotes = new ArrayList<>();
                //this is the child of tuplet, in the grammar either a duplet, triplet, or quadruplet
                ParseTree<ABCGrammar> tupletChild; 
                for (ParseTree<ABCGrammar> child : tree.children()) {
                    if (!child.getName().equals(ABCGrammar.WHITESPACE)) {//exclude whitespace children
                        tupletChild = child;
                        for (ParseTree<ABCGrammar> element : tupletChild.children()) {
                            //the children of tupletChild are chords or notes
                            if (!element.getName().equals(ABCGrammar.WHITESPACE)) { //exclude whitespace children
                                tupletNotes.add(buildElement(element, header, accidentalMap));
                            }
                        }
                        return NoteElement.tuplet(tupletNotes);
                    }
                }
                throw new RuntimeException("Tuplet should have a none-whitespace child");
                
                
            default:
                throw new RuntimeException("Should not reach default clause");
            }
        
    }
    
    /**
     * Takes in a noteElement tree and parses the duration of it
     * @param noteElement element to parse duration of
     * @return double duration in beats
     */
    static double parseDuration(ParseTree<ABCGrammar> noteElement, Header header) {
        double duration;
        if (noteElement.childrenByName(ABCGrammar.DURATION).size() == 0) {
            duration = 1; //default 
        }else {
            String durationFraction = noteElement.childrenByName(ABCGrammar.DURATION).get(0).getContents();
            String numeratorString, denominatorString;
            int numerator, denominator;
            if (durationFraction.contains("/")) {
                numeratorString = durationFraction.substring(0, durationFraction.indexOf("/"));
                denominatorString = durationFraction.substring(durationFraction.indexOf("/") + 1);
            
                numerator = numeratorString.isEmpty() ? 1:Integer.parseInt(numeratorString);
                denominator = denominatorString.isEmpty() ? 2:Integer.parseInt(denominatorString);
            } else {
                numerator = Integer.parseInt(durationFraction);
                denominator = 1;
            }
            duration = numerator / (double)denominator;
        }
        double beats = header.getDefaultLength() / header.getBeatLength() * duration;
        return beats;
    }
    
    
}