package br.com.sapereaude.maskedEditText;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class MaskedEditText extends EditText implements TextWatcher {

	private String mask;
	private char charRepresentation;
	private int[] rawToMask;
	private RawText rawText;
	private boolean editingBefore;
	private boolean editingOnChanged;
	private boolean editingAfter;
	private int[] maskToRaw;
	private char[] charsInMask;
	private int selection;
	private boolean initialized;
	private boolean ignore;
	protected int maxRawLength;
	
//	public MaskedEditText(Context context) {
//		super(context);
//		init();
//		mask = "";
//		charRepresentation = "";
//	}
	
	public MaskedEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialized = false;
		init();
		
		TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.MaskedEditText);
		mask = attributes.getString(R.styleable.MaskedEditText_mask);
		String representation = attributes.getString(R.styleable.MaskedEditText_char_representation);
		
		if(representation == null) {
			charRepresentation = '#';
		}
		else {
			charRepresentation = representation.charAt(0);
		}
		
		generatePositionArrays();
		
		rawText = new RawText();
		selection = rawToMask[0];
		this.setText(mask.replace(charRepresentation, ' '));
		ignore = false;
		maxRawLength = maskToRaw[previousValidPosition(mask.length() - 1)] + 1;
		initialized = true;
		
		setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(hasFocus()) {
					MaskedEditText.this.setSelection(lastValidPosition());
				}
			}
		});
		
		// Ignoring enter key presses
		setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				return true;
			}
		});
	}

	private void generatePositionArrays() {
		int[] aux = new int[mask.length()];
		maskToRaw = new int[mask.length()];
		String charsInMaskAux = "";
		
		int charIndex = 0;
		for(int i = 0; i < mask.length(); i++) {
			char currentChar = mask.charAt(i);
			if(currentChar == charRepresentation) {
				aux[charIndex] = i;
				maskToRaw[i] = charIndex++;
			}
			else {
				String charAsString = Character.toString(currentChar);
				if(!charsInMaskAux.contains(charAsString)) {
					charsInMaskAux = charsInMaskAux.concat(charAsString);
				}
				maskToRaw[i] = -1;
			}
		}
		if(charsInMaskAux.indexOf(' ') < 0) {
			charsInMaskAux = charsInMaskAux + " ";
		}
		charsInMask = charsInMaskAux.toCharArray();
		
		rawToMask = new int[charIndex];
		for (int i = 0; i < charIndex; i++) {
			rawToMask[i] = aux[i];
		}
	}
	
	private void init() {
		addTextChangedListener(this);
		editingAfter = false;
		editingBefore = false;
		editingOnChanged = false;
	}
	
	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		if(!editingBefore) {
			editingBefore = true;
			if(start >= mask.length()) {
				ignore = true;
			}
			Range range = calculateRange(start, start + count);
			if(range.getStart() != -1) {
				rawText.subtractFromString(range);
			}
			if(count > 0) {
				selection = previousValidPosition(start);
			}
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		if(!editingOnChanged && editingBefore) {
			editingOnChanged = true;
			if(ignore) {
				return;
			}
			if(count > 0) {
				int startingPosition = maskToRaw[nextValidPosition(start)];
				String addedString = s.subSequence(start, start + count).toString();
				count = rawText.addToString(clear(addedString), startingPosition, maxRawLength);
				if(initialized) {
					selection = nextValidPosition(start + count);
				}
			}
		}
	}

	@Override
	public void afterTextChanged(Editable s) {
		if(!editingAfter && editingBefore && editingOnChanged) {
			editingAfter = true;
			setText(makeMaskedText());
			
			setSelection(selection);
			
			editingBefore = false;
			editingOnChanged = false;
			editingAfter = false;
			ignore = false;
		}
	}
	
	@Override
	protected void onSelectionChanged(int selStart, int selEnd) {
		if(initialized) {
			selStart = fixSelection(selStart);
			selEnd = fixSelection(selEnd);
			setSelection(selStart, selEnd);
		}
		super.onSelectionChanged(selStart, selEnd);
	}
	
	private int fixSelection(int selection) {
		if(selection > lastValidPosition()) {
			return lastValidPosition();
		} 
		else {
			return nextValidPosition(selection);
		}
	}

	private int nextValidPosition(int currentPosition) {
		while(currentPosition < maskToRaw.length && maskToRaw[currentPosition] == -1) {
			currentPosition++;
		}
		return currentPosition;
	}
	
	private int previousValidPosition(int currentPosition) {
		while(currentPosition >= 0 && maskToRaw[currentPosition] == -1) {
			currentPosition--;
			if(currentPosition < 0) {
				return nextValidPosition(0);
			}
		}
		return currentPosition;
	}
	
	private int lastValidPosition() {
		if(rawText.length() == maxRawLength) {
			return rawToMask[rawText.length() - 1] + 1;
		}
		return nextValidPosition(rawToMask[rawText.length()]);
	}
	
	private String makeMaskedText() {
		char[] maskedText = mask.replace(charRepresentation, ' ').toCharArray();
		for(int i = 0; i < rawToMask.length; i++) {
			if(i < rawText.length()) {
				maskedText[rawToMask[i]] = rawText.charAt(i);
			}
			else {
				maskedText[rawToMask[i]] = ' ';
			}
		}
		return new String(maskedText);
	}

	private Range calculateRange(int start, int end) {
		Range range = new Range();
		for(int i = start; i <= end && i < mask.length(); i++) {
			if(maskToRaw[i] != -1) {
				if(range.getStart() == -1) {
					range.setStart(maskToRaw[i]);
				}
				range.setEnd(maskToRaw[i]);
			}
		}
		if(end == mask.length()) {
			range.setEnd(rawText.length());
		}
		if(range.getStart() == range.getEnd() && start < end) {
			int newStart = previousValidPosition(range.getStart() - 1);
			if(newStart < range.getStart()) {
				range.setStart(newStart);
			}
		}
		return range;
	}
	
	private String clear(String string) {
		for(char c : charsInMask) {
			string = string.replace(Character.toString(c), "");
		}
		return string;
	}
}
