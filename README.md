# Vuldra
A command line interface for scanning source code for vulnerabilities.

## Development

1. Install OpenJDK
2. Set a `OPENAI_API_KEY` environment variable on your host or in a .env file in the project root
3. To test the CLI tool, run `./gradlew run --args=""` with any desired arguments. This does not support prompting for input.

### Documentation
- [Clikt](https://ajalt.github.io/clikt/) is used for the CLI tool 
- [OpenAI API](https://beta.openai.com/docs/api-reference/introduction) is used for the text generation
- [openai-kotlin](https://github.com/aallam/openai-kotlin) is used as a wrapper for the OpenAI API

### Acknowledgements
- [kotlin-cli-starter](https://github.com/jmfayard/kotlin-cli-starter) was used as a great template for this project