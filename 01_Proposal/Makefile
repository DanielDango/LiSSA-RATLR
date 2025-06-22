# Directories
SRC_DIR := Images/svg
OUT_DIR := Slides/images
COPY_DIR := Document/graphics
COVER_SRC := Document/thesis.bib

# Find all SVGs in the source directory
SVG_FILES := $(wildcard $(SRC_DIR)/*.svg)

# Convert full paths into just base filenames (e.g. diagram.svg)
SVG_BASENAMES := $(notdir $(SVG_FILES))

# Define the PDF and copied SVG output files
PDF_FILES := $(SVG_BASENAMES:%.svg=$(OUT_DIR)/%.pdf)
COPIED_SVGS := $(SVG_BASENAMES:%.svg=$(COPY_DIR)/%.svg)
COVER_DEST := Slides/presentation.bib



# Inkscape command and flags
INKSCAPE := inkscape
INKSCAPE_FLAGS := --export-type=pdf

# Default target
all: $(PDF_FILES) $(COPIED_SVGS) $(COVER_DEST)

# Rule to convert each SVG to PDF in flat output dir
$(OUT_DIR)/%.pdf: $(SRC_DIR)/%.svg
	@if not exist "$(OUT_DIR)" mkdir "$(OUT_DIR)"
	$(INKSCAPE) $(INKSCAPE_FLAGS) -o "$@" "$<"


# Rule to copy cover.pdf (Windows-safe)
$(COVER_DEST): $(COVER_SRC)
	@powershell -Command "New-Item -ItemType Directory -Force -Path '$(dir $@)' > $null"
	@powershell -Command "Copy-Item -LiteralPath '$<' -Destination '$@' -Force"

# Pattern rule to copy a single SVG (Windows CMD version)
$(COPY_DIR)/%.svg: $(SRC_DIR)/%.svg
	@if not exist "$(COPY_DIR)" mkdir "$(COPY_DIR)"
	@powershell -Command "Copy-Item -LiteralPath '$<' -Destination '$@' -Force"



# Clean output
clean:
	@if exist "$(OUT_DIR)" rmdir /S /Q "$(OUT_DIR)"
	@if exist "$(COPY_DIR)" rmdir /S /Q "$(COPY_DIR)"
