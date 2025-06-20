# Directories
SRC_DIR := Images/svg
OUT_DIR := Slides/images

# Find all SVGs in the source directory
SVG_FILES := $(wildcard $(SRC_DIR)/*.svg)

# Convert full paths into just base filenames (e.g. diagram.svg)
SVG_BASENAMES := $(notdir $(SVG_FILES))

# Define the PDF output files
PDF_FILES := $(SVG_BASENAMES:%.svg=$(OUT_DIR)/%.pdf)

# Inkscape command and flags
INKSCAPE := inkscape
INKSCAPE_FLAGS := --export-type=pdf

# Default target
all: $(PDF_FILES)

# Rule to convert each SVG to PDF in flat output dir
$(OUT_DIR)/%.pdf: $(SRC_DIR)/%.svg
	$(INKSCAPE) $(INKSCAPE_FLAGS) -o $@ $<

# Clean output
clean:
	rm -rf $(OUT_DIR)
