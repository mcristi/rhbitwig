package com.akai.fire.sequence;

import java.util.List;

public class NoteAction {
	public enum Type {
		CLEAR, COPY_PAD;
	}

	private final int destPadIndex;
	private final int srcPadIndex;
	private final Type type;
	private final List<StepData> copyNotes;
	private final long createdAt = System.currentTimeMillis();

	NoteAction(final int srcPadIndex, final int destPadIndex, final Type type) {
		this(srcPadIndex, destPadIndex, type, null);
	}

	NoteAction(final int srcPadIndex, final int destPadIndex, final Type type, final List<StepData> copyNotes) {
		this.destPadIndex = destPadIndex;
		this.srcPadIndex = srcPadIndex;
		this.type = type;
		this.copyNotes = copyNotes;
	}

	/** The pad selection that triggers the action follows at once; anything later is a leftover. */
	public boolean isExpired() {
		return System.currentTimeMillis() - createdAt > 1000;
	}

	public Type getType() {
		return type;
	}

	public int getSrcPadIndex() {
		return srcPadIndex;
	}

	public int getDestPadIndex() {
		return destPadIndex;
	}

	public List<StepData> getCopyNotes() {
		return copyNotes;
	}

}
