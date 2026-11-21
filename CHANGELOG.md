# Changelog

## 1.0.0 (first release)

For Minecraft 1.21.11 on Fabric (Loader 0.18.1+, Fabric API required). Client-side only, so it works on any server.

### Textboxes on your HUD
- Add as many textboxes as you like. They stay on screen while you play.
- Press **J** to open the editor and **K** to show or hide every box at once. Both keys can be changed in Controls.
- Boxes are saved automatically to `config/textboxes.json`.

### In-game editor
- Add, duplicate, delete, bring forward and send back boxes from the toolbar.
- Drag a box to move it, or drag its corner to resize it. The arrow keys nudge it (hold Shift for 10px).
- Double-click a box, or press Enter, to edit its text.
- The settings panel can sit on the right or left, or be hidden.
- Shortcuts: Delete removes a box, Ctrl+D duplicates it.

### Text styling
- Multi-line text with word wrap. Minecraft color codes (`§`) work too.
- Fonts: Default, Uniform, Galactic (enchanting table), Illageralt, or any custom font id from a resource pack.
- Bold, italic, underline, strikethrough and shadow.
- Scale, horizontal and vertical alignment, padding and line spacing.

### Colors and opacity
- Set the background, text, border and image tint separately.
- Pick colors with RGB sliders, a hex field or quick swatches, and set opacity for each one.
- Border width can be adjusted.

### Images
- Show PNG, JPG, GIF or BMP images from `config/textboxes/images/`.
- An image picker shows thumbnails, with buttons to open the folder and reload.
- Image fit modes: stretch, fit, or fill. "Fit to aspect" resizes the box to match the image.

### Items and images inside the text
- Insert any item with `{item:diamond}`, including items from other mods (`{item:modid:thing}`).
- Insert images with `{img:file.png}`.
- The **+ Item** picker lets you search all 1,500+ items. The **+ Image** picker inserts pictures from your images folder.
- Rename items with `{item:paper|5}`. They show up exactly like an anvil-renamed item, so resource packs that change textures by name work (for example, numbered paper tiles).
- Icon size can be adjusted (4 to 64px), and icons line up with the text.
- Item icons and inline images fade with the box's text opacity.
- If a token is mistyped, it shows in red so it's easy to find and fix.
