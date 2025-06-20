# Makefile to convert SVG to PDF using Inkscape

# The SVG files you want to convert (you can add more)
SVG_FILES := $(wildcard *.svg)

# Output directory for PDFs
OUTPUT_DIR := ..\..\Slides\images

# Inkscape command
INKSCAPE := inkscape

# Flags for Inkscape
INKSCAPE_FLAGS := --export-type=pdf

# Define the rule to convert each SVG file to a PDF
$(OUTPUT_DIR)/%.pdf: %.svg
	$(INKSCAPE) $(INKSCAPE_FLAGS) -o $@ $<

# Default target: Convert all SVG files to PDF
all: $(SVG_FILES:%.svg=$(OUTPUT_DIR)/%.pdf)

# Clean up generated PDFs
clean:
	rm -rf $(OUTPUT_DIR)