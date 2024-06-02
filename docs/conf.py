# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

import os

# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information

project = 'Hephaestus'
copyright = '2024, Technical University of Munich, Chair for Applied Software Engineering'
author = 'Chair for Applied Software Engineering, TUM'
release = '0.1'

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

master_doc = "index"
extensions = [
    "sphinx_rtd_theme",
    "sphinx.ext.autodoc",
    'sphinxcontrib.plantuml',
]

exclude_patterns = ['_build', 'Thumbs.db', '.DS_Store', 'venv', '.venv']

autodoc_mock_imports = [
    'sqlalchemy',
    'uvicorn',
    'dotenv',
    'fastapi',
    'pydantic',
    'httpx',
]

plantuml_output_format='svg'

# java = 'JAVA_HOME' in os.environ and f"{os.environ['JAVA_HOME']}/bin/java" or 'java'
local_plantuml_path = os.path.join(os.path.dirname(__file__), 'bin/plantuml.jar')
plantuml = f'java -jar {local_plantuml_path}'

# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output

html_theme = 'sphinx_rtd_theme'
html_context = {
    "display_github": True,
    "github_user": "ls1intum",
    "github_repo": "Hephaestus",
    "github_version": "develop",
    "conf_py_path": "/docs/",
}
html_logo = "images/logo.svg"
html_theme_options = {
    'logo_only': True,
    'display_version': False,
    'style_nav_header_background': '#3b82f6',
}
html_style = 'css/style.css'

html_static_path = ['_static']
