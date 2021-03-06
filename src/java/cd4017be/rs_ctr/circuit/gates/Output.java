package cd4017be.rs_ctr.circuit.gates;

import cd4017be.rs_ctr.circuit.editor.GeneratedType;
import cd4017be.rscpl.gui.GateTextureHandler;
import cd4017be.rscpl.gui.ISpecialRender;
import cd4017be.rscpl.gui.SchematicBoard;

/** Redstone signal output
 * @author CD4017BE */
public class Output extends End implements ISpecialRender {

	public int portID;

	public Output(GeneratedType type, int index) {
		super(type, index);
	}

	@Override
	public void draw(SchematicBoard board, int x, int y) {
		int l = Math.min(label.length(), 5);
		GateTextureHandler.drawTinyText(
			board.parent.getDraw(), label, x - l * 4 + 7, y + 5, l,
			board.parent.zLevel
		);
	}

	@Override
	public Object getParam(int i) {
		return i >= 0 ? super.getParam(i) : i == -1 ? portID : 2 << portID;
	}

}
