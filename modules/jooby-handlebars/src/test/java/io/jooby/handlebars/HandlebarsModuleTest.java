/*
 * Jooby https://jooby.io
 * Apache License Version 2.0 https://jooby.io/LICENSE.txt
 * Copyright 2014 Edgar Espina
 */
package io.jooby.handlebars;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.github.jknack.handlebars.Handlebars;
import com.typesafe.config.ConfigFactory;
import io.jooby.Environment;
import io.jooby.ModelAndView;
import io.jooby.test.MockContext;

public class HandlebarsModuleTest {
  public static class User {
    private String firstname;

    private String lastname;

    public User(String firstname, String lastname) {
      this.firstname = firstname;
      this.lastname = lastname;
    }

    public String getFirstname() {
      return firstname;
    }

    public String getLastname() {
      return lastname;
    }
  }

  @Test
  public void render() throws Exception {
    Handlebars handlebars =
        HandlebarsModule.create()
            .build(new Environment(getClass().getClassLoader(), ConfigFactory.empty()));
    HbsTemplateEngine engine = new HbsTemplateEngine(handlebars, Arrays.asList(".hbs"));
    MockContext ctx = new MockContext();
    ctx.getAttributes().put("local", "var");
    String output =
        engine.render(
            ctx,
            new ModelAndView("index.hbs").put("user", new User("foo", "bar")).put("sign", "!"));
    assertEquals("Hello foo bar var!", output.trim());
  }

  @Test
  public void renderFileSystem() throws Exception {
    Handlebars handlebars =
        HandlebarsModule.create()
            .setTemplatesPath(Paths.get("src", "test", "resources", "views").toString())
            .build(new Environment(getClass().getClassLoader(), ConfigFactory.empty()));
    HbsTemplateEngine engine = new HbsTemplateEngine(handlebars, Arrays.asList(".hbs"));
    MockContext ctx = new MockContext();
    ctx.getAttributes().put("local", "var");
    String output =
        engine.render(
            ctx,
            new ModelAndView("index.hbs").put("user", new User("foo", "bar")).put("sign", "!"));
    assertEquals("Hello foo bar var!", output.trim());
  }
}
