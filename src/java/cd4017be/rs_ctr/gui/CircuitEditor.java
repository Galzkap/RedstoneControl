package cd4017be.rs_ctr.gui;

import cd4017be.lib.Gui.HidableSlot;
import cd4017be.lib.Gui.comp.Button;
import cd4017be.lib.Gui.comp.FileBrowser;
import cd4017be.lib.Gui.comp.GuiFrame;
import cd4017be.lib.Gui.comp.InfoTab;
import cd4017be.lib.Gui.comp.Progressbar;
import cd4017be.lib.Gui.comp.TextField;
import cd4017be.lib.Gui.comp.Tooltip;
import static cd4017be.lib.network.GuiNetworkHandler.*;
import cd4017be.lib.util.TooltipUtil;
import cd4017be.lib.util.Utils;
import cd4017be.rs_ctr.Main;
import cd4017be.rs_ctr.circuit.CircuitCompiler;
import cd4017be.rs_ctr.circuit.CompiledCircuit;
import cd4017be.rs_ctr.circuit.editor.CircuitInstructionSet;
import cd4017be.rs_ctr.tileentity.Assembler;
import cd4017be.rs_ctr.tileentity.Editor;
import cd4017be.rscpl.editor.BoundingBox2D;
import cd4017be.rscpl.editor.Gate;
import cd4017be.rscpl.editor.GateType;
import cd4017be.rscpl.editor.InvalidSchematicException;
import cd4017be.rscpl.gui.GatePalette;
import cd4017be.rscpl.gui.ISpecialCfg;
import cd4017be.rscpl.gui.SchematicBoard;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import static cd4017be.rs_ctr.tileentity.Editor.*;
import static cd4017be.rscpl.editor.Schematic.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import cd4017be.lib.Gui.ModularGui;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;


/**
 * @author CD4017BE
 *
 */
public class CircuitEditor extends ModularGui {

	private static final File dir = new File(Minecraft.getMinecraft().mcDataDir, "circuitSchematics");
	private static final int FILE_MAGIC = 0x4017CB00;
	private static final ResourceLocation BG_TEX = new ResourceLocation(Main.ID, "textures/gui/editor.png");
	private static final ResourceLocation COMP_TEX = new ResourceLocation(Main.ID, "textures/gui/palette.png");

	public final Editor tile;
	public final SchematicBoard board;
	public final GatePalette palette;
	public final GuiFrame cfg;
	public final GuiErrorMarker error;
	private GuiDebugger debug;

	/**
	 * @param container
	 */
	public CircuitEditor(Editor tile, EntityPlayer player) {
		super(tile.getContainer(player, 0));
		this.tile = tile;
		GuiFrame comps = new GuiFrame(this, 256, 256, 17).background(BG_TEX, 0, 0).title(tile.getName(), 0.1F);
		comps.texture(COMP_TEX, 256, 256);
		new InfoTab(comps, 7, 8, 7, 6, "gui.rs_ctr.editor.info");
		new TextField(comps, 120, 8, 128, 4, 64, ()-> tile.name, (name)-> {
			sendPkt(A_NAME, name);
			tile.lastFile = null;
		}).tooltip("gui.rs_ctr.editor.title");
		(this.cfg = new GuiFrame(comps, 76, 27, 2)).position(173, 173);
		this.board = new SchematicBoard(comps, 8, 16, tile.schematic, this::changeSelPart);
		(this.palette = new GatePalette(comps, CircuitInstructionSet.TABS, 7, 173, board::place) {
			@Override
			protected String getTooltip(GateType t) {
				int cost = CircuitInstructionSet.INS_SET.getCost(t);
				return super.getTooltip(t) + "\n" + TooltipUtil.format("gate.cost", cost & 0xff, cost >> 8 & 0xff);
			}
		}).title("gui.rs_ctr.palette", 0.5F);
		new Button(comps, 7, 7, 162, 162, 0, ()-> board.selPart != null ? 1 : 0, board::del).texture(186, 0).tooltip("gui.rs_ctr.editor.del");
		new Button(comps, 16, 14, 8, 158, 2, ()-> palette.enabled() ? 1 : 0, this::togglePalette).texture(162, 52).tooltip("gui.rs_ctr.palette.open#");
		new Button(comps, 16, 16, 232, 210, 0, null, (i)-> {tile.lastFile = null; sendCommand(A_NEW);}).tooltip("gui.rs_ctr.editor.new");
		new Button(comps, 16, 16, 214, 210, 0, null, (b)-> selFile(false)).tooltip("gui.rs_ctr.editor.load");
		new Button(comps, 16, 16, 196, 210, 0, null, (b)-> {
			if (b == Button.B_RIGHT || tile.lastFile == null) selFile(true);
			else save(tile.lastFile);
		}).tooltip("gui.rs_ctr.editor.save");
		new Button(comps, 16, 16, 174, 210, 0, null, this::compile).tooltip("gui.rs_ctr.editor.compile");
		
		new Progressbar(comps, 56, 4, 192, 232, 200, 0, Progressbar.H_FILL_R, ()-> this.tile.ingreds[0], 0, 112);
		new Progressbar(comps, 56, 4, 192, 238, 200, 6, Progressbar.H_FILL_R, ()-> this.tile.ingreds[1], 0, 112);
		new Progressbar(comps, 56, 4, 192, 244, 200, 12, Progressbar.H_FILL_R, ()-> this.tile.ingreds[2], 0, 112);
		new Progressbar(comps, 56, 2, 192, 233, 200, 4, Progressbar.PIXELS, ()-> this.tile.ingreds[3], 0, 112);
		new Progressbar(comps, 56, 2, 192, 239, 200, 10, Progressbar.PIXELS, ()-> this.tile.ingreds[4], 0, 112);
		new Progressbar(comps, 56, 2, 192, 245, 200, 16, Progressbar.PIXELS, ()-> this.tile.ingreds[5], 0, 112);
		new Tooltip(comps, 56, 16, 192, 232, "gui.rs_ctr.editor.ingreds", ()-> new Object[] {
			this.tile.ingreds[3], this.tile.ingreds[0], this.tile.ingreds[4], this.tile.ingreds[1], this.tile.ingreds[5], this.tile.ingreds[2]
		});
		
		Assembler ass = tile.getAssembler();
		if (ass != null)
			new Button(comps, 10, 9, 177, 200, 0, ()-> 0, (b)-> sendCommand(A_GO)).texture(168, 89).tooltip("gui.rs_ctr.to_assembler");
		
		this.compGroup = comps;
		this.error = new GuiErrorMarker(this);
		palette.setEnabled(false);
		changeSelPart();
		togglePalette(0);
	}

	void togglePalette(int s) {
		boolean hide = !palette.enabled();
		palette.setEnabled(hide);
		for (Slot slot : inventorySlots.inventorySlots)
			if (slot instanceof HidableSlot)
				((HidableSlot)slot).hideSlot(hide);
		drawInvTitle = !hide;
	}

	void changeSelPart() {
		cfg.clear();
		cfg.titleY = -11;
		BoundingBox2D<Gate> part = board.selPart;
		if (part != null && part.owner != null) {
			Gate g = part.owner;
			cfg.background(COMP_TEX, 180, 36).title("gate." + g.type.name.replace(':', '.'), 0.5F);
			String s = cfg.title;
			int i = s.indexOf('\n');
			if (i >= 0) s = s.substring(0, i);
			if (fontRenderer.getStringWidth(s) > cfg.w)
				s = fontRenderer.trimStringToWidth(s, cfg.w - fontRenderer.getStringWidth("..")) + "..";
			cfg.title = s;
			new TextField(cfg, 74, 7, 1, 1, 20, ()-> g.label, this::sendLabel).tooltip("gui.rs_ctr.opLabel");
			if (g instanceof ISpecialCfg)
				((ISpecialCfg)g).setupCfgGUI(cfg, this::updateCfg);
			cfg.init(cfg.screenWidth, cfg.screenHeight, cfg.zLevel, fontRenderer);
		} else {
			cfg.bgTexture = null;
			cfg.title = null;
			tile.ingreds[6] = InvalidSchematicException.NO_ERROR;
		}
	}

	private void selFile(boolean save) {
		if (save) dir.mkdirs();
		GuiFrame fb = new FileBrowser((GuiFrame)compGroup, (f)-> {
			f.close();
			if (save) save(f.getFile());
			else load(f.getFile());
		}, (fn) -> fn.endsWith(".cb"))
			.setFile(new File(dir, tile.name + ".cb").getAbsoluteFile())
			.title(save ? "gui.rs_ctr.save_file" : "gui.rs_ctr.load_file", 0.5F);
		fb.init(width, height, zLevel, fontRenderer);
		fb.position(8, 8);
	}

	void load(File file) {
		try {
			PacketBuffer data = preparePacket(container);
			data.writeByte(A_LOAD);
			int p = data.writerIndex();
			FileInputStream fis = new FileInputStream(file);
			int i;
			while ((i = fis.available()) > 0)
				data.writeBytes(fis, i);
			fis.close();
			if (data.getInt(p) != FILE_MAGIC)
				sendChat(TooltipUtil.translate("msg.rs_ctr.invalid_file"));
			tile.lastFile = file;
			GNH_INSTANCE.sendToServer(data);
		} catch(FileNotFoundException e) {
			sendChat(TooltipUtil.format("msg.rs_ctr.no_file", file));
		} catch(Exception e) {
			sendChat("\u00a74" + e.toString());
		}
	}

	void save(File file) {
		try {
			ByteBuf data = Unpooled.buffer();
			data.writeInt(FILE_MAGIC);
			int i = data.writerIndex();
			data.writeByte(0);
			data.setByte(i, data.writeCharSequence(tile.name, Utils.UTF8));
			tile.schematic.serialize(data);
			file.createNewFile();
			FileOutputStream fos = new FileOutputStream(file);
			data.readBytes(fos, data.writerIndex());
			fos.close();
			tile.lastFile = file;
			sendChat(TooltipUtil.translate("msg.rs_ctr.save_succ"));
		} catch (Exception e) {
			sendChat("\u00a74" + e.toString());
		}
	}

	void compile(int b) {
		if (b == Button.B_RIGHT) {
			compGroup.remove(debug);
			try {
				tile.ingreds[6] = InvalidSchematicException.NO_ERROR;
				CompiledCircuit cc = CircuitCompiler.INSTANCE.compile(tile.schematic.operators);
				if (cc.compileWarning != null)
					tile.ingreds[6] = cc.compileWarning.compact();
				debug = new GuiDebugger((GuiFrame)compGroup, cc);
				debug.init(width, height, zLevel, fontRenderer);
				debug.position(8, 8);
			} catch (InvalidSchematicException e) {
				tile.ingreds[6] = e.compact();
			} catch (Throwable e) {
				Main.LOG.error("internal compilation error: ", e);
			}
		} else if (b == Button.B_LEFT)
			sendCommand(A_COMPILE);
	}

	@Override
	public void updateScreen() {
		super.updateScreen();
		board.update();
		error.update(tile.ingreds[6]);
		if (debug != null) debug.update();
	}

	public void sendLabel(String label) {
		BoundingBox2D<Gate> part = board.selPart;
		PacketBuffer pkt = preparePacket(container);
		pkt.writeByte(SET_LABEL).writeByte(part.owner.index);
		pkt.writeCharSequence(label, Utils.UTF8);
		GNH_INSTANCE.sendToServer(pkt);
	}

	public void updateCfg() {
		BoundingBox2D<Gate> part = board.selPart;
		if (part == null || part.owner == null) return;
		PacketBuffer pkt = preparePacket(container);
		pkt.writeByte(SET_VALUE).writeByte(part.owner.index);
		if (part.owner.writeCfg(pkt))
			GNH_INSTANCE.sendToServer(pkt);
	}

}
