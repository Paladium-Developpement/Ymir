<div align="center">

<img src="https://i.imgur.com/LvJD97T.png" alt="logo" style="width:25%">

# Ymir

<div align="center" >
  <img align="center" src="https://img.shields.io/badge/version-1.0.0 (abcdef)-blue">
  <img align="center" src="https://img.shields.io/badge/minecraft-forge 1.7.10-blue">
  <img align="center" src="https://img.shields.io/badge/maintainer-Zeldown-orange">
  <img align="center" src="https://img.shields.io/maintenance/yes/9999">
</div>

<br>

Créez, chargez, copiez et supprimez des mondes Minecraft pendant que le serveur tourne, en une ligne.
<br><br>
Ymir paie à votre place tous les pièges du multi-monde sur Forge et Bukkit : dimensions fantômes, fichiers de région jamais relâchés, identifiants de dimension qui se télescopent, mondes vides ignorés par les mods.

[Pourquoi](#pourquoi) • [Utilisation](#utilisation) • [Options](#options) • [Gradle](#gradle) • [Build](#build) • [Publish](#publish)

</div>

## Pourquoi

Créer un monde à chaud, c'est trois lignes. Le faire sans rien casser, beaucoup moins. Ymir règle ce que l'on découvre en production :

- **Plus de monde fantôme.** Une dimension déchargée est désinscrite. Sans ça, un joueur qui se reconnecte force le serveur à recréer sa dimension par le chemin vanilla, dans un dossier `DIM<id>` parallèle au vôtre, qui reste chargé et sauvegardé à vie.
- **Plus de conflit d'identifiants.** L'identifiant de dimension est relâché à chaque chargement, donc deux mondes ne peuvent jamais revendiquer le même numéro, quoi qu'il arrive au redémarrage.
- **Fichiers réellement relâchés.** Les fichiers de région restent ouverts après un déchargement : la suppression échoue sur NFS et un monde recréé au même endroit ressert les anciens chunks. Ymir vide la file d'écriture puis ferme et retire du cache les fichiers du monde.
- **Suppression sans course.** Le dossier est renommé dans une corbeille avant d'être effacé en arrière-plan : on peut recréer un monde du même nom immédiatement.
- **Un vide vraiment vide.** Le type `VOID` est un vrai type de monde Forge, pas un générateur Bukkit — ce dernier est ignoré dès qu'un mod fournit le provider du monde. Les chunks sont marqués décorés, donc aucun mod ne vient y semer de minerai.
- **Déchargement automatique** des mondes inactifs, et rien à faire à l'arrêt du serveur.

## Utilisation

### Créer

```java
final YmirWorld world = Ymir.create(YmirWorldConfig.create("arene")
    .type(YmirWorldType.VOID)
    .platform(4)
    .gamerule("keepInventory", true)
    .unloadAfter(Duration.ofMinutes(5)));

world.teleport(player);
```

`create` construit le monde s'il n'existe pas et le charge sinon, avec la même configuration. Le monde se décharge seul après cinq minutes sans personne, et se recharge à la première téléportation.

### Retrouver

```java
Ymir.get("arene");          // par nom
Ymir.get(player);           // le monde Ymir où se trouve un joueur, s'il y en a un
Ymir.get(player.worldObj);  // idem à partir d'un monde Minecraft
Ymir.getWorlds();           // tout ce qu'Ymir connaît
Ymir.exists("arene");       // le dossier est-il sur le disque
```

### Copier, supprimer

```java
world.copy("arene-2").thenAccept(copy -> copy.teleport(player));
world.delete();
```

La copie se fait hors du thread serveur et vous rend la main dessus : la suite du `thenAccept` s'exécute là où vous pouvez toucher au jeu. La suppression exige un monde vide de joueurs, sinon elle lève une `YmirWorldException`.

Les deux existent aussi à partir d'une simple configuration, sans jamais charger le monde :

```java
Ymir.copy(YmirWorldConfig.create("arene"), YmirWorldConfig.create("arene-2"));
Ymir.delete(YmirWorldConfig.create("arene"));
```

Un monde chargé est déchargé proprement avant l'opération, un monde absent du serveur est traité directement sur le disque. C'est la forme à utiliser pour régénérer ou archiver un monde que personne n'occupe.

N'appelez jamais `.join()` sur ces futures depuis le thread serveur : elles se terminent dessus, vous attendriez votre propre tick. Enchaînez avec `thenAccept` ou `whenComplete`.

### Partir d'un modèle

```java
Ymir.createAsync(YmirWorldConfig.create("donjon-" + id)
    .template(new File(server, "templates/donjon"))
    .unloadAfter(Duration.ofMinutes(1)))
    .thenAccept(donjon -> donjon.teleport(player));
```

`createAsync` copie le modèle hors du thread serveur, puis crée le monde sur le thread serveur.

### Suivre le cycle de vie

```java
@SubscribeEvent
public void onCreate(final YmirWorldEvent.Create event) {
    event.getWorld().setGamerule("doDaylightCycle", false);
}
```

`Create`, `Load`, `Unload` et `Delete` sont postés sur le bus Forge.

## Options

Toutes se déclarent sur `YmirWorldConfig.create(nom)`.

| Option | Défaut | Rôle |
|---|---|---|
| `type` | `NORMAL` | `NORMAL`, `FLAT`, `AMPLIFIED`, `LARGE_BIOMES` ou `VOID` |
| `seed` | aléatoire | graine, numérique ou textuelle |
| `structures` | `true` | génération des structures |
| `environment` | `NORMAL` | `NORMAL`, `NETHER` ou `THE_END` |
| `spawn` | selon le type | point d'apparition imposé, sans la recherche vanilla |
| `platform` | aucune | plateforme carrée de ce rayon sous le spawn, utile pour un monde vide |
| `gamerule` | aucune | gamerules appliquées à chaque chargement |
| `unloadAfter` | jamais | déchargement après ce temps sans joueur |
| `keepSpawnInMemory` | `false` | garde les chunks du spawn chargés |
| `autoSave` | `true` | sauvegarde automatique |
| `template` | aucun | dossier copié au lieu de générer le monde |
| `link` | aucun | dossier qui suit le monde, copié et supprimé avec lui, résolu depuis son nom |
| `worldGuard` | `false` | copie la configuration WorldGuard du monde principal et charge les régions du nouveau monde |

Un dossier lié se déclare par une fonction, pour qu'Ymir sache le nommer quand vous copiez le monde :

```java
YmirWorldConfig.create("arene").link(name -> new File(Ymir.getContainer(), "data/" + name));
```

## Gradle

To include Ymir in your project using Gradle, add the following dependency to your `build.gradle` file:

```gradle
repositories {
    maven {
        name = "forge"
        url = "http://repository.palagitium.dev/artifactory/Paladium-Dev"
        credentials {
            username = getRepositoryUser()
            password = getRepositoryPassword()
        }
    }
}

def getRepositoryUser() {
    if(System.getenv('MAVEN_REPO_USER') != null) {
        return System.getenv('MAVEN_REPO_USER')
    }else {
        return project.findProperty('MAVEN_REPO_USER')
    }

    return ""
}

def getRepositoryPassword() {
    if(System.getenv('MAVEN_REPO_PASS') != null) {
        return System.getenv('MAVEN_REPO_PASS')
    }else {
        return project.findProperty('MAVEN_REPO_PASS')
    }

    return ""
}

configurations.all {
   resolutionStrategy {
   	cacheDynamicVersionsFor 60, 'seconds'
   }
}

dependencies {
    compile "fr.paladium:ymir:+:dev"
}
```

Déclarez ensuite la dépendance dans votre mod :

```java
@Mod(modid = Constants.MOD_ID, version = Constants.VERSION, dependencies = "required-after:ymir")
```

## Build

```sh
gradlew build         # Build le projet dans le dossier Ymir/build/libs.
```

## Publish

```sh
gradlew publish       # Met en ligne le projet sur repository.palagitium.dev
```
