# Minimal makefile for Sphinx documentation
#

# You can set these variables from the command line, and also
# from the environment for the first two.
SPHINXAUTOBUILD = sphinx-autobuild

ALLSPHINXLIVEOPTS = $(ALLSPHINXOPTS) -q \
   --port 0 \
   --host 127.0.0.1 \
   --open-browser \
   --delay 1 \
   --ignore "*.swp" \
   --ignore "*.pdf" \
   --ignore "*.log" \
   --ignore "*.out" \
   --ignore "*.toc" \
   --ignore "*.aux" \
   --ignore "*.idx" \
   --ignore "*.ind" \
   --ignore "*.ilg" \
   --ignore "*.tex" \
   --watch .

SPHINXOPTS    ?=
SPHINXBUILD   ?= sphinx-build
SOURCEDIR     = .
BUILDDIR      = _build

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

livehtml:
	$(SPHINXAUTOBUILD) -b html $(ALLSPHINXLIVEOPTS) $(SOURCEDIR) $(BUILDDIR)
	@echo
	@echo "Build finished. The HTML pages are in $(BUILDDIR)."

.PHONY: help Makefile
# .PHONY: livehtml

# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)