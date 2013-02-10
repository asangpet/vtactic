package ak.vtactic.primitives;

public class Translator {
	static Translator _translator = new Translator();
	
	public String doTranslate(String operand) {
		switch (operand) {
		case "10.4.20.1":return "A";
		case "10.4.20.2":return "B";
		case "10.4.20.3":return "C";
		case "10.4.20.4":return "D";
		case "10.4.20.5":return "E";
		case "10.4.20.6":return "F";
		case "10.4.20.7":return "G";
		case "10.4.20.8":return "H";
		case "10.4.20.9":return "I";
		}
		return operand;		
	}
	
	public static String translate(String operand) {
		return _translator.doTranslate(operand);
	}

	public static void setTranslator(Translator translator) {
		_translator = translator;
	}
}
