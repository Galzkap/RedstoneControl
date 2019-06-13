package cd4017be.rs_ctr.gui;

import cd4017be.lib.Gui.ModularGui;
import cd4017be.lib.Gui.comp.Button;
import cd4017be.lib.Gui.comp.FormatText;
import cd4017be.lib.Gui.comp.GuiFrame;
import cd4017be.lib.network.GuiNetworkHandler;
import cd4017be.lib.util.Utils;
import cd4017be.rs_ctr.Main;
import cd4017be.rs_ctr.tileentity.Processor;
import cd4017be.rscpl.gui.StateEditor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;


/**
 * @author CD4017BE
 *
 */
public class GuiProcessor extends ModularGui {

	private static final ResourceLocation TEX = new ResourceLocation(Main.ID, "textures/gui/palette.png");

	private final StateEditor state;

	public GuiProcessor(Processor tile, EntityPlayer player) {
		super(tile.getContainer(player, 0));
		GuiFrame comps = new GuiFrame(this, 168, 24, 3).background(TEX, 0, 90).title(tile.getName(), 0.5F);
		new FormatText(comps, 116, 7, 8, 16, "\\%s", ()-> new Object[] {tile.getError()});
		new Button(comps, 18, 9, 125, 15, 0, ()-> tile.update ? 1 : 0, (i)-> sendPkt((byte)-1)).texture(168, 148).tooltip("gui.rs_ctr.processor.run");
		this.state = StateEditor.of(comps, tile.circuit, tile.getIOLabels(), 12, this::updateVar);
		state.move(0, 24);
		new Button(comps, 18, 9, 143, 15, 2, ()-> state.hex ? 1 : 0, (s)-> state.hex = s != 0).texture(168, 166).tooltip("gui.rs_ctr.hex#");
		this.compGroup = comps;
	}

	private void updateVar(int i) {
		if (i < 0) return;
		PacketBuffer buf = GuiNetworkHandler.preparePacket(container);
		buf.writeByte(i);
		NBTTagCompound nbt = state.circuit.getState().nbt;
		Utils.writeTag(buf, nbt.getTag(state.keys[i]));
		GuiNetworkHandler.GNH_INSTANCE.sendToServer(buf);
	}

	@Override
	public void initGui() {
		super.initGui();
		ySize += state.h;
		guiTop = (height - ySize) / 2;
		compGroup.position(guiLeft, guiTop);
	}

}
