/*                                                                        */
/* Copyright IBM Corp. 2023                                               */
/*                                                                        */
package com.ibm.cics.cip.bank.core.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedField;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import org.springframework.context.annotation.Configuration;

/**
 * Spring {@link Configuration @Configuration} component that carries the frozen
 * z/OS Connect <em>envelope</em> JSON naming behaviour forward into the
 * pure-Java {@code bank-core} module, preserving byte-for-byte wire
 * compatibility with the preserved front ends (feature F-019).
 *
 * <p><strong>What it provides.</strong> The single responsibility of this class
 * is to host the referenceable {@link EnvelopeNamingStrategy} nested type. That
 * strategy is ported verbatim from the legacy z/OS Connect interface module's
 * {@code JsonPropertyNamingStrategy}: it strips the conventional 3-character
 * prefix from each field/getter/setter member name so that Jackson's derived
 * JSON property names line up with the original z/OS Connect envelope. The outer
 * class deliberately carries no other state or beans &mdash; an empty
 * {@code @Configuration} whose only purpose is to publish the strategy as a
 * stable, public class literal.</p>
 *
 * <p><strong>Per-DTO opt-in, never a global override.</strong> The legacy code
 * applied the strategy <em>per class</em> via {@code @JsonNaming}, and this
 * module reproduces that exactly. This class intentionally does <em>not</em>
 * register a module-wide property-naming strategy (no
 * {@code ObjectMapper}/{@code Jackson2ObjectMapperBuilderCustomizer} default).
 * A global override would apply {@code substring(3)} to <em>every</em>
 * serialised object &mdash; including error envelopes and any non-DTO response
 * &mdash; and would corrupt the frozen contract. Keeping the strategy as an
 * opt-in class literal preserves the source semantics and confines the
 * behaviour to the wire DTOs that explicitly request it.</p>
 *
 * <p><strong>Contract for the {@code core/dto} package.</strong> The canonical,
 * public fully-qualified name of the strategy is
 * {@code com.ibm.cics.cip.bank.core.config.JacksonConfig.EnvelopeNamingStrategy}.
 * Every wire DTO in {@code com.ibm.cics.cip.bank.core.dto} references it as
 * {@code @JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)} (importing
 * {@code com.ibm.cics.cip.bank.core.config.JacksonConfig}). Where a DTO also
 * declares an explicit {@code @JsonProperty("Comm...")}, Jackson honours that
 * explicit name and the strategy does not apply &mdash; matching the source
 * behaviour precisely.</p>
 *
 * @see EnvelopeNamingStrategy
 * @see PropertyNamingStrategy
 */
@Configuration
public class JacksonConfig
{

	/**
	 * Jackson {@link PropertyNamingStrategy} that reproduces the z/OS Connect
	 * envelope wire names by stripping the conventional 3-character member-name
	 * prefix (see {@link #convert(String)}). Ported verbatim, including its
	 * {@code serialVersionUID}, from the interface module's
	 * {@code JsonPropertyNamingStrategy}.
	 *
	 * <p><strong>Canonical FQN.</strong>
	 * {@code com.ibm.cics.cip.bank.core.config.JacksonConfig.EnvelopeNamingStrategy}.
	 * Wire DTOs in {@code com.ibm.cics.cip.bank.core.dto} opt in per class with
	 * {@code @JsonNaming(JacksonConfig.EnvelopeNamingStrategy.class)}.</p>
	 *
	 * <p><strong>Why {@code public static}.</strong> The type is declared
	 * {@code public static} so Jackson can instantiate it through its implicit
	 * no-arg constructor when a DTO names it in {@code @JsonNaming}. A non-static
	 * inner class would require an enclosing {@code JacksonConfig} instance and
	 * would fail to instantiate.</p>
	 *
	 * <p>Extending {@link PropertyNamingStrategy} and overriding the three
	 * {@code name*} methods is the supported extension model under the Jackson
	 * 2.x baseline managed by Spring Boot 3.5.11 (only the inner
	 * {@code PropertyNamingStrategyBase} helper is deprecated, not these
	 * overrides).</p>
	 */
	public static class EnvelopeNamingStrategy extends PropertyNamingStrategy
	{

		/**
		 * Serialisation version identifier, preserved from the source
		 * {@code JsonPropertyNamingStrategy} because {@link PropertyNamingStrategy}
		 * implements {@link java.io.Serializable}.
		 */
		private static final long serialVersionUID = -5634229355538397996L;

		/**
		 * Derives the JSON wire name for a directly-bound field by stripping the
		 * conventional 3-character prefix from the Java field name.
		 *
		 * @param config      the active mapper configuration (unused; required by
		 *                    the overridden signature)
		 * @param field       the annotated field whose name is being translated
		 * @param defaultName the default name Jackson would otherwise use (unused;
		 *                    the translation is derived from the field name)
		 * @return the field name with its conventional 3-character prefix removed
		 * @see #convert(String)
		 */
		@Override
		public String nameForField(MapperConfig<?> config, AnnotatedField field, String defaultName)
		{
			return convert(field.getName());
		}

		/**
		 * Derives the JSON wire name for a getter-bound property by stripping the
		 * conventional 3-character prefix from the getter method name.
		 *
		 * @param config      the active mapper configuration (unused; required by
		 *                    the overridden signature)
		 * @param method      the annotated getter whose name is being translated
		 * @param defaultName the default name Jackson would otherwise use (unused;
		 *                    the translation is derived from the method name)
		 * @return the method name with its conventional 3-character prefix removed
		 * @see #convert(String)
		 */
		@Override
		public String nameForGetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName)
		{
			return convert(method.getName());
		}

		/**
		 * Derives the JSON wire name for a setter-bound property by stripping the
		 * conventional 3-character prefix from the setter method name.
		 *
		 * @param config      the active mapper configuration (unused; required by
		 *                    the overridden signature)
		 * @param method      the annotated setter whose name is being translated
		 * @param defaultName the default name Jackson would otherwise use (unused;
		 *                    the translation is derived from the method name)
		 * @return the method name with its conventional 3-character prefix removed
		 * @see #convert(String)
		 */
		@Override
		public String nameForSetterMethod(MapperConfig<?> config, AnnotatedMethod method, String defaultName)
		{
			return convert(method.getName());
		}

		/**
		 * Strips the conventional 3-character member-name prefix, reproducing the
		 * source strategy's {@code input.substring(3)} behaviour exactly for every
		 * valid member name of three or more characters &mdash; which is every
		 * real DTO member following the envelope convention.
		 *
		 * <p>A defensive guard returns the input unchanged when it is {@code null}
		 * or shorter than three characters. This is a pure safety measure: no
		 * conforming DTO member name is ever shorter than its 3-character prefix,
		 * so the guard never alters the frozen wire contract; it only prevents a
		 * {@link StringIndexOutOfBoundsException} on a pathological input. For any
		 * valid 3+-character name the canonical result remains
		 * {@code input.substring(3)}.</p>
		 *
		 * @param input the raw Java member name (field or method name)
		 * @return {@code input} with its first three characters removed when it is
		 *         non-{@code null} and at least three characters long; otherwise
		 *         {@code input} unchanged
		 */
		private String convert(String input)
		{
			if (input == null || input.length() < 3)
			{
				return input;
			}
			return input.substring(3);
		}
	}
}
