import { render } from "@testing-library/react-native";
import * as React from "react";

import { ThemedText } from "../ThemedText";

it(`renders correctly`, () => {
  const tree = render(<ThemedText>Snapshot test!</ThemedText>).toJSON();

  expect(tree).toMatchSnapshot();
});
