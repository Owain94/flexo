package net.runelite.client.plugins.flexoredchins;

public enum RedChinFlexoState
{
	DISABLED("Disabled"),

	COMPASS("Click compass"),
	CAMERA("Position camera"),

	GET_TRAP_LOCATIONS("Grab locations"),

	WALK_FIRST("Walk 1st"),
	SETUP_FIRST("Walk 1st"),

	WALK_SECOND("Setup 1st"),
	SETUP_SECOND("Walk 2nd"),

	WALK_THIRD("Setup 2nd"),
	SETUP_THIRD("Walk 3rd"),

	WALK_FOURTH("Setup 3rd"),
	SETUP_FOURTH("Walk 4th"),

	WALK_FIFTH("Setup 4th"),
	SETUP_FIFTH("Walk 5th"),

	STUB_FIFTH("Setup 5th"),
	STUB_FALLEN_TRAP("Fallen trap"),

	HUNTING_CHINS("Hunting"),
	HUNTING_MISCLICK("Fix misclick"),
	RESET_TRAP("Resetting trap"),
	TRAP_LOCATION_CHECK("Check location"),
	TRAP_SETUP_CHECK("Checking setup"),
	TRAP_RESET_CHECK("Emptying trap"),
	TRAP_TICK_CHECK("Checking tick"),
	LOST_TRAPS("Lost traps");

	private final String name;

	RedChinFlexoState(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
