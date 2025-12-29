module.exports = {
    singleQuote: true,
    printWidth: 120,
    trailingComma: 'all',
    semi: true,
    tabWidth: 4,
    overrides: [
        {
            files: ['*.yaml', '*.yml', '*.json'],
            options: {
                tabWidth: 2,
            },
        },
    ],
};
