# Install

From the root of the project, run:

```bash
npm ci
```

Before running the example app, you need to create an .env file, so from the `expo` directory, run:

```bash
cp .env.example .env
vim .env
```

And edit your env file with the proper values for the environment you're trying to test against.

# Usage

To run the example app, from the root of the project, run:

```bash
nx ios expo-example # Run on iOS
nx android expo-example # Run on Android
```

Depending on the changes you're making locally, you may need to clean up the `ios/` and `android/` directory within `expo-example`,
as this is generated as part of the build and won't always be properly re-generated on changes.
