package com.google.gson.typeadapters;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Test;

public final class RuntimeTypeAdapterFactoryTest {
  private abstract static class Shape {
    int x;
    int y;
  }

  private static final class Circle extends Shape {
    int radius;
  }

  @Test
  public void recognizeSubtypesDeserializesSubtypeWithoutTypeField() {
    RuntimeTypeAdapterFactory<Shape> factory =
        RuntimeTypeAdapterFactory.of(Shape.class)
            .registerSubtype(Circle.class, "Circle")
            .recognizeSubtypes();

    Gson gson = new GsonBuilder().registerTypeAdapterFactory(factory).create();

    Circle circle = gson.fromJson("{\"radius\":4}", Circle.class);
    assertEquals(4, circle.radius);
  }
}
